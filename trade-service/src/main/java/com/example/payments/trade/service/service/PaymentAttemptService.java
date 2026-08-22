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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentAttemptService {
  private final PaymentAttemptRepository repository;
  private final PaymentCallbackRecordRepository callbackRepository;
  private final PaymentChannelAdapter channel;
  private final OrderService orderService;
  private final com.example.payments.trade.service.mapper.PaymentOutboxEventRepository
      outboxRepository;
  private final ObjectMapper objectMapper;

  public PaymentAttemptService(
      PaymentAttemptRepository repository,
      PaymentCallbackRecordRepository callbackRepository,
      PaymentChannelAdapter channel,
      OrderService orderService,
      com.example.payments.trade.service.mapper.PaymentOutboxEventRepository outboxRepository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.callbackRepository = callbackRepository;
    this.channel = channel;
    this.orderService = orderService;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public PaymentAttempt create(PaymentOrder order, String behavior) {
    String attemptId = UUID.randomUUID().toString();
    var request =
        new PaymentChannelAdapter.PaymentChannelRequest(
            attemptId,
            order.orderId(),
            order.merchantId(),
            order.currency(),
            order.paymentMethod(),
            order.amount().toPlainString(),
            behavior);
    var result = channel.createPayment(request);
    PaymentAttemptStatus status = statusOf(result.status());
    Instant now = Instant.now();
    var attempt =
        repository.insert(
            new PaymentAttempt(
                attemptId,
                order.orderId(),
                "simulated-channel",
                result.channelOrderId(),
                1,
                status,
                "{\"amount\":\""
                    + order.amount().toPlainString()
                    + "\",\"currency\":\""
                    + order.currency()
                    + "\"}",
                result.responseSnapshot(),
                result.failureCode(),
                now,
                status.isTerminal() ? now : null,
                0));
    coordinateOrder(attempt);
    return attempt;
  }

  @Transactional
  public PaymentAttempt callback(String rawPayload, String signature, String callbackId) {
    var callback =
        channel.verifyCallback(
            new PaymentChannelAdapter.PaymentCallbackRequest(rawPayload, signature, callbackId));
    if (!callbackRepository.claim(callbackId, rawPayload, signature, Instant.now())) {
      return repository
          .findByChannelRequestNo("simulated-channel", callback.channelOrderId())
          .orElseThrow(
              () -> new ResponseStatusException(HttpStatus.CONFLICT, "duplicate callback"));
    }
    var attempt =
        repository
            .findByChannelRequestNo("simulated-channel", callback.channelOrderId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found"));
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
            callback.rawPayload(),
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
        channel.queryPayment(
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
        channel.cancelPayment(
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
    var request =
        new PaymentChannelAdapter.PaymentChannelRequest(
            attemptId,
            order.orderId(),
            order.merchantId(),
            order.currency(),
            order.paymentMethod(),
            order.amount().toPlainString(),
            behavior);
    var result = channel.createPayment(request);
    var status = statusOf(result.status());
    Instant now = Instant.now();
    var attempt =
        repository.insert(
            new PaymentAttempt(
                attemptId,
                order.orderId(),
                "simulated-channel",
                result.channelOrderId(),
                attemptNo,
                status,
                "{\"amount\":\""
                    + order.amount().toPlainString()
                    + "\",\"currency\":\""
                    + order.currency()
                    + "\"}",
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
