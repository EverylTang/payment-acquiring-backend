package com.example.payments.trade.service.service;

import com.example.payments.trade.service.domain.PaymentAttempt;
import com.example.payments.trade.service.domain.PaymentAttemptStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentAttemptRepository;
import com.example.payments.trade.service.mapper.PaymentCallbackRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PaymentAttemptService {
  private final PaymentAttemptRepository repository;
  private final PaymentCallbackRecordRepository callbackRepository;
  private final ChannelAdapterRegistry channelAdapters;
  private final PlatformChannelConfigurationClient channelConfiguration;
  private final ChannelRequestSigner channelRequestSigner;
  private final OrderService orderService;
  private final com.example.payments.trade.service.mapper.PaymentOutboxEventRepository
      outboxRepository;
  private final MerchantNotificationOutboxService merchantNotificationOutboxService;
  private final ObjectMapper objectMapper;

  @Transactional
  public PaymentAttempt create(PaymentOrder order, String behavior) {
    String attemptId = UUID.randomUUID().toString();
    var runtime = channelConfiguration.resolve(order);
    var channel = channelAdapters.required(runtime.provider(), runtime.signatureProfile());
    var request =
        new PaymentChannelAdapter.PaymentChannelRequest(
            attemptId,
            order.orderId(),
            order.merchantId(),
            order.currency(),
            order.paymentMethod(),
            order.payerPayableAmount().toPlainString(),
            behavior,
            runtime,
            channelRequestSigner.sign(runtime, requestFields(order, runtime, attemptId, behavior)));
    var result = channel.createPayment(request);
    PaymentAttemptStatus status = statusOf(result.status());
    Instant now = Instant.now();
    var attempt =
        repository.insert(
            new PaymentAttempt(
                attemptId,
                order.orderId(),
                runtime.channelId(),
                result.channelOrderId(),
                1,
                status,
                requestSnapshot(order, runtime),
                result.responseSnapshot(),
                result.failureCode(),
                now,
                status.isTerminal() ? now : null,
                0));
    coordinateOrder(attempt);
    return attempt;
  }

  @Transactional
  public PaymentAttempt callback(
      String channelId, String rawPayload, String signature, String callbackId) {
    var currentRuntime = channelConfiguration.resolve(channelId);
    var currentChannel =
        channelAdapters.required(currentRuntime.provider(), currentRuntime.signatureProfile());
    var channelOrderId = currentChannel.callbackChannelOrderId(rawPayload);
    var attempt =
        repository
            .findByChannelRequestNo(channelId, channelOrderId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found"));
    var runtime = callbackRuntime(attempt, currentRuntime);
    var callback =
        channelAdapters
            .required(runtime.provider(), runtime.signatureProfile())
            .verifyCallback(
                new PaymentChannelAdapter.PaymentCallbackRequest(
                    rawPayload, signature, callbackId, runtime));
    if (!channelOrderId.equals(callback.channelOrderId())) {
      throw new IllegalArgumentException("callback channel order id mismatch");
    }
    if (!callbackRepository.claim(callbackId, rawPayload, signature, Instant.now())) {
      return repository
          .findByChannelRequestNo(channelId, callback.channelOrderId())
          .orElseThrow(
              () -> new ResponseStatusException(HttpStatus.CONFLICT, "duplicate callback"));
    }
    if (attempt.status().isTerminal()) {
      callbackRepository.markProcessed(
          callbackId,
          attempt.attemptId(),
          callback.channelOrderId(),
          attempt.status().name(),
          Instant.now());
      return attempt;
    }
    var nextStatus = statusOf(callback.status());
    var next =
        new PaymentAttempt(
            attempt.attemptId(),
            attempt.orderId(),
            attempt.channelId(),
            attempt.channelRequestNo(),
            attempt.attemptNo(),
            nextStatus,
            attempt.requestSnapshot(),
            callbackResponseSnapshot(callback.rawPayload()),
            null,
            attempt.startedAt(),
            nextStatus.isTerminal() ? Instant.now() : null,
            attempt.version() + 1);
    if (!attempt.status().canTransitionTo(next.status())) {
      callbackRepository.markProcessed(
          callbackId,
          attempt.attemptId(),
          callback.channelOrderId(),
          attempt.status().name(),
          Instant.now());
      return attempt;
    }
    if (!repository.update(attempt.status(), next)) {
      return repository.findByAttemptId(attempt.attemptId()).orElse(next);
    }
    callbackRepository.markProcessed(
        callbackId,
        next.attemptId(),
        callback.channelOrderId(),
        next.status().name(),
        Instant.now());
    coordinateOrder(next);
    return next;
  }

  public PaymentAttempt get(String attemptId, String orderId) {
    var attempt =
        repository
            .findByAttemptId(attemptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found"));
    if (!attempt.orderId().equals(orderId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found");
    }
    return attempt;
  }

  @Transactional
  public PaymentAttempt query(String attemptId) {
    var attempt =
        repository
            .findByAttemptId(attemptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found"));
    if (attempt.status().isTerminal()) return attempt;
    var result =
        adapterFor(attempt)
            .queryPayment(
                new PaymentChannelAdapter.PaymentChannelQuery(
                    attempt.attemptId(), attempt.channelRequestNo()));
    return applyResult(attempt, result);
  }

  @Transactional
  public PaymentAttempt cancel(String attemptId) {
    var attempt =
        repository
            .findByAttemptId(attemptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found"));
    if (attempt.status().isTerminal()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "terminal attempt cannot be canceled");
    }
    return applyResult(
        attempt,
        adapterFor(attempt)
            .cancelPayment(
                new PaymentChannelAdapter.PaymentChannelQuery(
                    attempt.attemptId(), attempt.channelRequestNo())));
  }

  @Transactional
  public PaymentAttempt retry(String attemptId, PaymentOrder order) {
    var previous =
        repository
            .findByAttemptId(attemptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found"));
    if (!(previous.status() == PaymentAttemptStatus.FAILED
        || previous.status() == PaymentAttemptStatus.TIMEOUT
        || previous.status() == PaymentAttemptStatus.UNKNOWN)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "attempt is not retryable");
    }
    int attemptNo = repository.countByOrderId(order.orderId()) + 1;
    return create(order, "SUCCESS", attemptNo);
  }

  private PaymentAttempt create(PaymentOrder order, String behavior, int attemptNo) {
    String attemptId = UUID.randomUUID().toString();
    var runtime = channelConfiguration.resolve(order);
    var channel = channelAdapters.required(runtime.provider(), runtime.signatureProfile());
    var request =
        new PaymentChannelAdapter.PaymentChannelRequest(
            attemptId,
            order.orderId(),
            order.merchantId(),
            order.currency(),
            order.paymentMethod(),
            order.payerPayableAmount().toPlainString(),
            behavior,
            runtime,
            channelRequestSigner.sign(runtime, requestFields(order, runtime, attemptId, behavior)));
    var result = channel.createPayment(request);
    var status = statusOf(result.status());
    Instant now = Instant.now();
    var attempt =
        repository.insert(
            new PaymentAttempt(
                attemptId,
                order.orderId(),
                runtime.channelId(),
                result.channelOrderId(),
                attemptNo,
                status,
                requestSnapshot(order, runtime),
                result.responseSnapshot(),
                result.failureCode(),
                now,
                status.isTerminal() ? now : null,
                0));
    coordinateOrder(attempt);
    return attempt;
  }

  @Transactional
  public PaymentAttempt timeout(String attemptId) {
    var attempt =
        repository
            .findByAttemptId(attemptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found"));
    if (attempt.status().isTerminal()) return attempt;
    return applyResult(
        attempt,
        new PaymentChannelAdapter.PaymentChannelResult(
            attempt.channelRequestNo(),
            "TIMEOUT",
            "{\"status\":\"TIMEOUT\"}",
            "QUERY_LIMIT_EXCEEDED",
            null));
  }

  private PaymentAttempt applyResult(
      PaymentAttempt attempt, PaymentChannelAdapter.PaymentChannelResult result) {
    var status = statusOf(result.status());
    var next =
        new PaymentAttempt(
            attempt.attemptId(),
            attempt.orderId(),
            attempt.channelId(),
            attempt.channelRequestNo(),
            attempt.attemptNo(),
            status,
            attempt.requestSnapshot(),
            result.responseSnapshot(),
            result.failureCode(),
            attempt.startedAt(),
            status.isTerminal() ? Instant.now() : null,
            attempt.version() + 1);
    if (!attempt.status().canTransitionTo(next.status())) return attempt;
    if (!repository.update(attempt.status(), attempt.version(), next)) {
      return repository.findByAttemptId(attempt.attemptId()).orElse(attempt);
    }
    coordinateOrder(next);
    return repository.findByAttemptId(attempt.attemptId()).orElse(next);
  }

  private PaymentChannelAdapter adapterFor(PaymentAttempt attempt) {
    try {
      var snapshot = objectMapper.readValue(attempt.requestSnapshot(), java.util.Map.class);
      var provider = String.valueOf(snapshot.get("provider"));
      return channelAdapters.required(provider, String.valueOf(snapshot.get("signatureProfile")));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("支付尝试缺少渠道运行配置", exception);
    }
  }

  private String requestSnapshot(PaymentOrder order, ChannelRuntimeContext runtime) {
    try {
      return objectMapper.writeValueAsString(
          java.util.Map.of(
              "amount", order.amount().toPlainString(),
              "payerPayableAmount", order.payerPayableAmount().toPlainString(),
              "feeAmount", order.feeAmount().toPlainString(),
              "feeBearer", order.feeBearer(),
              "currency", order.currency(),
              "channelId", runtime.channelId(),
              "provider", runtime.provider(),
              "requestUrl", runtime.requestUrl(),
              "signatureProfile", runtime.signatureProfile(),
              "settings", runtime.settings()));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法记录渠道运行配置", exception);
    }
  }

  private ChannelRuntimeContext callbackRuntime(
      PaymentAttempt attempt, ChannelRuntimeContext currentRuntime) {
    return currentRuntime;
  }

  private String callbackResponseSnapshot(String rawPayload) {
    try {
      return objectMapper.writeValueAsString(java.util.Map.of("rawPayload", rawPayload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法记录渠道回调", exception);
    }
  }

  private java.util.Map<String, String> requestFields(
      PaymentOrder order, ChannelRuntimeContext runtime, String attemptId, String behavior) {
    var fields = new java.util.LinkedHashMap<String, String>();
    fields.put("attemptId", attemptId);
    fields.put("orderId", order.orderId());
    fields.put("merchantId", order.merchantId());
    fields.put("currency", order.currency());
    fields.put("paymentMethod", order.paymentMethod());
    // Channel-facing amount is the amount the payer must actually complete.
    fields.put("amount", order.payerPayableAmount().toPlainString());
    fields.put("orderAmount", order.amount().toPlainString());
    fields.put("payerPayableAmount", order.payerPayableAmount().toPlainString());
    fields.put("feeAmount", order.feeAmount().toPlainString());
    fields.put("feeBearer", order.feeBearer());
    fields.put("behavior", behavior);
    runtime
        .settings()
        .forEach(
            (key, value) -> {
              if (!isSignatureControl(key) && value instanceof String) {
                fields.putIfAbsent(key, (String) value);
              }
            });
    return fields;
  }

  private boolean isSignatureControl(String key) {
    return "signatureFields".equals(key)
        || "signatureFieldName".equals(key)
        || "signatureSecretRole".equals(key);
  }

  private void coordinateOrder(PaymentAttempt attempt) {
    var orderStatus =
        switch (attempt.status()) {
          case SUCCESS -> com.example.payments.trade.service.domain.OrderStatus.SUCCESS;
          case FAILED -> com.example.payments.trade.service.domain.OrderStatus.FAILED;
          case CANCELED -> com.example.payments.trade.service.domain.OrderStatus.CANCELED;
          case PROCESSING, CREATED -> com.example.payments.trade.service.domain.OrderStatus.PAYING;
          case TIMEOUT, UNKNOWN -> com.example.payments.trade.service.domain.OrderStatus.UNKNOWN;
        };
    orderService.callback(attempt.orderId(), orderStatus);
    if (attempt.status() == PaymentAttemptStatus.SUCCESS) {
      var order = orderService.get(attempt.orderId());
      String eventId = "PAYMENT_SUCCEEDED:" + attempt.orderId() + ":" + attempt.attemptId();
      try {
        String payload =
            objectMapper.writeValueAsString(
                new PaymentSucceededEvent(
                    eventId,
                    "PAYMENT_SUCCEEDED",
                    1,
                    Instant.now(),
                    "trade-service",
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    attempt.version(),
                    attempt.orderId(),
                    attempt.attemptId(),
                    order.merchantId(),
                    order.amount(),
                    order.currency()));
        outboxRepository.insert(eventId, attempt.orderId(), "PAYMENT_SUCCEEDED", payload);
        merchantNotificationOutboxService.enqueuePaymentSuccess(order);
      } catch (JsonProcessingException exception) {
        throw new IllegalStateException("payment success event serialization failed", exception);
      }
    }
  }

  private record PaymentSucceededEvent(
      String eventId,
      String eventType,
      int schemaVersion,
      Instant occurredAt,
      String producer,
      String requestId,
      String traceId,
      long aggregateVersion,
      String orderId,
      String attemptId,
      String merchantId,
      java.math.BigDecimal amount,
      String currency) {}

  private static PaymentAttemptStatus statusOf(String value) {
    try {
      return PaymentAttemptStatus.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException exception) {
      return PaymentAttemptStatus.UNKNOWN;
    }
  }
}
