package com.example.payments.trade.service.service;

import com.example.payments.trade.service.domain.PaymentAttempt;
import com.example.payments.trade.service.domain.PaymentAttemptStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentAttemptRepository;
import com.example.payments.trade.service.mapper.PaymentCallbackRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
  private final ExpiredPaymentSuccessExceptionService expiredSuccessExceptionService;
  private final ObjectMapper objectMapper;
  private final PaymentSuccessEventSigner paymentSuccessEventSigner;

  @Autowired(required = false)
  private RedisDistributedLockService attemptCreationLock;

  @Value("${trade.channel-callback.base-url:}")
  private String channelCallbackBaseUrl;

  @Transactional
  public PaymentAttempt create(PaymentOrder order) {
    if (attemptCreationLock == null) return createOrReuseOpenAttempt(order);
    try {
      return attemptCreationLock.executeWithLock(
          "payment-attempt:create:" + order.orderId(),
          UUID.randomUUID().toString(),
          Duration.ofMinutes(3),
          Duration.ofSeconds(15),
          () -> createOrReuseOpenAttempt(order));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "支付尝试创建被中断", exception);
    } catch (RuntimeException exception) {
      if (exception.getMessage() != null
          && exception.getMessage().startsWith("Failed to acquire lock:")) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "支付尝试正在创建，请稍后查询订单", exception);
      }
      throw exception;
    }
  }

  private PaymentAttempt createOrReuseOpenAttempt(PaymentOrder order) {
    return repository.findLatestOpenByOrderId(order.orderId()).orElseGet(() -> create(order, 1));
  }

  @Transactional
  public PaymentAttempt callback(
      String channelId, String rawPayload, String signature, String callbackId) {
    var currentRuntime = channelConfiguration.resolve(channelId);
    var currentChannel =
        channelAdapters.required(currentRuntime.provider(), currentRuntime.signatureProfile());
    var channelOrderId = currentChannel.callbackChannelOrderId(rawPayload);
    var attempt = repository.findByChannelRequestNo(channelId, channelOrderId).orElse(null);
    if (attempt == null) {
      String merchantOrderId = currentChannel.callbackMerchantOrderId(rawPayload);
      attempt =
          repository
              .findLatestByOrderId(merchantOrderId)
              .filter(candidate -> candidate.channelId().equals(channelId))
              .filter(this::isProvisional)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, "payment attempt not found"));
    }
    var order = orderService.get(attempt.orderId());
    if (order.orderType() == com.example.payments.trade.service.domain.OrderType.PAYOUT) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "遗留出款尝试已隔离，需通过出款渠道迁移处理");
    }
    var runtime = callbackRuntime(attempt, currentRuntime);
    var callback =
        channelAdapters
            .required(runtime.provider(), runtime.signatureProfile())
            .verifyCallback(
                new PaymentChannelAdapter.PaymentCallbackRequest(
                    rawPayload,
                    signature,
                    callbackId,
                    order.orderId(),
                    order.currency(),
                    order.payerPayableAmount().toPlainString(),
                    runtime));
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
            callback.channelOrderId(),
            attempt.attemptNo(),
            nextStatus,
            attempt.requestSnapshot(),
            callbackResponseSnapshot(callback.rawPayload()),
            null,
            attempt.startedAt(),
            nextStatus.isTerminal() ? Instant.now() : null,
            attempt.version() + 1,
            attempt.paymentUrl(),
            attempt.qrCode());
    if (!attempt.status().canTransitionTo(next.status())) {
      callbackRepository.markProcessed(
          callbackId,
          attempt.attemptId(),
          callback.channelOrderId(),
          attempt.status().name(),
          Instant.now());
      return attempt;
    }
    if (!repository.update(attempt.status(), attempt.version(), next)) {
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

  public java.util.Optional<PaymentAttempt> latestForOrder(String orderId) {
    return repository.findLatestByOrderId(orderId);
  }

  /** Queues a merchant-requested refresh without bypassing channel query throttling. */
  @Transactional
  public PaymentAttempt requestQuery(String attemptId) {
    var attempt =
        repository
            .findByAttemptId(attemptId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "payment attempt not found"));
    if (!attempt.status().isTerminal()) {
      repository.requestImmediateQuery(attemptId, Instant.now());
    }
    return repository.findByAttemptId(attemptId).orElse(attempt);
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
    var runtime = callbackRuntime(attempt, channelConfiguration.resolve(attempt.channelId()));
    var result =
        channelAdapters
            .required(runtime.provider(), runtime.signatureProfile())
            .queryPayment(
                new PaymentChannelAdapter.PaymentChannelQuery(
                    attempt.attemptId(),
                    attempt.orderId(),
                    attempt.channelRequestNo(),
                    orderService.get(attempt.orderId()).currency(),
                    orderService.get(attempt.orderId()).payerPayableAmount().toPlainString(),
                    runtime));
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
    var runtime = callbackRuntime(attempt, channelConfiguration.resolve(attempt.channelId()));
    var channel = channelAdapters.required(runtime.provider(), runtime.signatureProfile());
    if (!channel.supportsCancellation()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "当前支付渠道未提供取消支付能力，请等待支付结果查询完成");
    }
    return applyResult(
        attempt,
        channel.cancelPayment(
            new PaymentChannelAdapter.PaymentChannelQuery(
                attempt.attemptId(),
                attempt.orderId(),
                attempt.channelRequestNo(),
                orderService.get(attempt.orderId()).currency(),
                orderService.get(attempt.orderId()).payerPayableAmount().toPlainString(),
                runtime)));
  }

  @Transactional
  public PaymentAttempt retry(String attemptId, PaymentOrder order) {
    order = orderService.requireActive(order.orderId());
    if (order.orderType() == com.example.payments.trade.service.domain.OrderType.PAYOUT) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "出款订单必须通过已配置的出款渠道执行");
    }
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
    var runtime = channelConfiguration.resolve(previous.channelId());
    if ("PAYPROO".equalsIgnoreCase(runtime.provider())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "PayProo 要求订单号全局唯一，请查询原支付尝试");
    }
    int attemptNo = repository.countByOrderId(order.orderId()) + 1;
    return create(order, attemptNo);
  }

  private PaymentAttempt create(PaymentOrder order, int attemptNo) {
    order = orderService.requireActive(order.orderId());
    String attemptId = UUID.randomUUID().toString();
    var runtime = channelConfiguration.resolve(order);
    var channel = channelAdapters.required(runtime.provider(), runtime.signatureProfile());
    var fields = requestFields(order, runtime, attemptId);
    var request =
        new PaymentChannelAdapter.PaymentChannelRequest(
            attemptId,
            order.orderId(),
            order.merchantId(),
            order.currency(),
            order.paymentMethod(),
            order.payerPayableAmount().toPlainString(),
            runtime,
            channel.ownsRequestSignature()
                ? new ChannelRequestSigner.ChannelRequestSignature(java.util.Map.of(), "sign", "")
                : channelRequestSigner.sign(runtime, fields),
            channelCallbackUrl(runtime),
            order.description(),
            payer(order));
    try {
      channel.validateCreate(request);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), exception);
    }
    Instant now = Instant.now();
    var provisional =
        repository.insert(
            new PaymentAttempt(
                attemptId,
                order.orderId(),
                runtime.channelId(),
                provisionalChannelOrderId(attemptId),
                attemptNo,
                PaymentAttemptStatus.PROCESSING,
                requestSnapshot(order, runtime, fields),
                "{\"state\":\"CREATE_PENDING\"}",
                null,
                now,
                null,
                0,
                null,
                null));
    coordinateOrder(provisional);
    try {
      return applyResult(provisional, channel.createPayment(request));
    } catch (ChannelRequestAmbiguousException exception) {
      return applyResult(
          provisional,
          new PaymentChannelAdapter.PaymentChannelResult(
              provisional.channelRequestNo(),
              "UNKNOWN",
              "{\"state\":\"CREATE_UNKNOWN\"}",
              "CREATE_TRANSPORT_AMBIGUOUS",
              null,
              null));
    }
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
            null,
            null));
  }

  public boolean isOrderExpired(PaymentAttempt attempt, Instant now) {
    return !orderService.get(attempt.orderId()).expireAt().isAfter(now);
  }

  private PaymentAttempt applyResult(
      PaymentAttempt attempt, PaymentChannelAdapter.PaymentChannelResult result) {
    var status = statusOf(result.status());
    var next =
        new PaymentAttempt(
            attempt.attemptId(),
            attempt.orderId(),
            attempt.channelId(),
            result.channelOrderId() == null || result.channelOrderId().isBlank()
                ? attempt.channelRequestNo()
                : result.channelOrderId(),
            attempt.attemptNo(),
            status,
            attempt.requestSnapshot(),
            result.responseSnapshot(),
            result.failureCode(),
            attempt.startedAt(),
            status.isTerminal() ? Instant.now() : null,
            attempt.version() + 1,
            result.paymentUrl() == null ? attempt.paymentUrl() : result.paymentUrl(),
            result.qrCode() == null ? attempt.qrCode() : result.qrCode());
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

  private String requestSnapshot(
      PaymentOrder order, ChannelRuntimeContext runtime, java.util.Map<String, String> fields) {
    try {
      var redactedFields = new java.util.LinkedHashMap<String, String>();
      fields.forEach(
          (key, value) -> redactedFields.put(key, isSensitive(key) ? "[REDACTED]" : value));
      var snapshot = new java.util.LinkedHashMap<String, Object>();
      snapshot.put("amount", order.amount().toPlainString());
      snapshot.put("payerPayableAmount", order.payerPayableAmount().toPlainString());
      snapshot.put("feeAmount", order.feeAmount().toPlainString());
      snapshot.put("feeBearer", order.feeBearer());
      snapshot.put("currency", order.currency());
      snapshot.put("channelId", runtime.channelId());
      snapshot.put("schemaVersion", runtime.schemaVersion());
      snapshot.put("provider", runtime.provider());
      snapshot.put("requestUrl", runtime.requestUrl());
      snapshot.put("signatureProfile", runtime.signatureProfile());
      snapshot.put("runtimeSettings", redactSettings(runtime.settings()));
      // Channel credentials are managed as readable backend configuration. Pin this attempt to the
      // credential material used for its create request so rotation does not break reconciliation.
      snapshot.put("runtimeCredentials", runtime.credentials());
      snapshot.put("channelRequest", redactedFields);
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法记录渠道运行配置", exception);
    }
  }

  private ChannelRuntimeContext callbackRuntime(
      PaymentAttempt attempt, ChannelRuntimeContext currentRuntime) {
    try {
      var snapshot = objectMapper.readValue(attempt.requestSnapshot(), java.util.Map.class);
      String provider = String.valueOf(snapshot.get("provider"));
      String signatureProfile = String.valueOf(snapshot.get("signatureProfile"));
      if (provider.isBlank()
          || "null".equals(provider)
          || signatureProfile.isBlank()
          || "null".equals(signatureProfile)) {
        throw new IllegalStateException("支付尝试缺少渠道签名快照");
      }
      int schemaVersion = 1;
      Object versionValue = snapshot.get("schemaVersion");
      if (versionValue instanceof Number number) {
        schemaVersion = number.intValue();
      }
      return new ChannelRuntimeContext(
          currentRuntime.channelId(),
          provider,
          snapshotText(snapshot, "requestUrl", currentRuntime.requestUrl()),
          signatureProfile,
          snapshotSettings(snapshot, currentRuntime.settings()),
          snapshotCredentials(snapshot, currentRuntime.credentials()),
          schemaVersion);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("支付尝试缺少渠道运行配置", exception);
    }
  }

  private String snapshotText(java.util.Map<?, ?> snapshot, String key, String fallback) {
    Object value = snapshot.get(key);
    return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
  }

  private java.util.Map<String, Object> snapshotSettings(
      java.util.Map<?, ?> snapshot, java.util.Map<String, Object> fallback) {
    Object value = snapshot.get("runtimeSettings");
    if (!(value instanceof java.util.Map<?, ?> settings)) return fallback;
    var copied = new java.util.LinkedHashMap<String, Object>();
    settings.forEach((key, item) -> copied.put(String.valueOf(key), item));
    return java.util.Map.copyOf(copied);
  }

  private java.util.Map<String, String> snapshotCredentials(
      java.util.Map<?, ?> snapshot, java.util.Map<String, String> fallback) {
    Object value = snapshot.get("runtimeCredentials");
    if (!(value instanceof java.util.Map<?, ?> credentials)) return fallback;
    var copied = new java.util.LinkedHashMap<String, String>();
    credentials.forEach(
        (key, item) -> {
          if (key != null && item != null && !String.valueOf(item).isBlank()) {
            copied.put(String.valueOf(key), String.valueOf(item));
          }
        });
    return java.util.Map.copyOf(copied);
  }

  private java.util.Map<String, Object> redactSettings(java.util.Map<String, Object> source) {
    var result = new java.util.LinkedHashMap<String, Object>();
    source.forEach(
        (key, value) -> {
          if (isSensitiveSetting(key)) return;
          if (value instanceof java.util.Map<?, ?> nested) {
            var nestedValues = new java.util.LinkedHashMap<String, Object>();
            nested.forEach(
                (nestedKey, nestedValue) -> {
                  if (!isSensitiveSetting(String.valueOf(nestedKey))) {
                    nestedValues.put(String.valueOf(nestedKey), nestedValue);
                  }
                });
            result.put(key, nestedValues);
          } else {
            result.put(key, value);
          }
        });
    return java.util.Map.copyOf(result);
  }

  private String channelCallbackUrl(ChannelRuntimeContext runtime) {
    String configured = runtime.setting("callbackBaseUrl");
    String base = configured.isBlank() ? channelCallbackBaseUrl : configured;
    if (base == null || base.isBlank()) return null;
    try {
      URI uri = URI.create(base.trim());
      if (!uri.isAbsolute()
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || !"https".equalsIgnoreCase(uri.getScheme())) {
        throw new IllegalArgumentException();
      }
      String normalized = uri.toASCIIString().replaceFirst("/+$", "");
      return normalized + "/api/v1/payments/channels/" + runtime.channelId() + "/callback";
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "渠道回调基础地址无效", exception);
    }
  }

  private boolean isProvisional(PaymentAttempt attempt) {
    return attempt.channelRequestNo().startsWith("pending:") && !attempt.status().isTerminal();
  }

  private static String provisionalChannelOrderId(String attemptId) {
    return "pending:" + attemptId;
  }

  private String callbackResponseSnapshot(String rawPayload) {
    try {
      return objectMapper.writeValueAsString(java.util.Map.of("rawPayload", rawPayload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法记录渠道回调", exception);
    }
  }

  private java.util.Map<String, String> requestFields(
      PaymentOrder order, ChannelRuntimeContext runtime, String attemptId) {
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

  private boolean isSensitive(String key) {
    String normalized = key.toLowerCase(java.util.Locale.ROOT);
    return normalized.contains("secret")
        || normalized.contains("password")
        || normalized.contains("token")
        || normalized.contains("signature")
        || normalized.contains("apikey")
        || normalized.contains("api_key");
  }

  private boolean isSensitiveSetting(String key) {
    return !isSignatureControl(key) && isSensitive(key);
  }

  @SuppressWarnings("unchecked")
  private java.util.Map<String, String> payer(PaymentOrder order) {
    try {
      var snapshot = objectMapper.readValue(order.merchantRequestSnapshot(), java.util.Map.class);
      if (!(snapshot.get("payer") instanceof java.util.Map<?, ?> source)) return java.util.Map.of();
      var payer = new java.util.LinkedHashMap<String, String>();
      source.forEach(
          (key, value) -> {
            if (key != null && value != null && !String.valueOf(value).isBlank()) {
              payer.put(String.valueOf(key), String.valueOf(value));
            }
          });
      return java.util.Map.copyOf(payer);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法读取付款人资料", exception);
    }
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
    var callbackResult = orderService.callbackResult(attempt.orderId(), orderStatus);
    var order = callbackResult.order();
    if (attempt.status() == PaymentAttemptStatus.SUCCESS) {
      if (!callbackResult.transitionedToSuccess()
          || order.status() != com.example.payments.trade.service.domain.OrderStatus.SUCCESS
          || order.orderType() == com.example.payments.trade.service.domain.OrderType.PAYOUT) {
        if (order.status() == com.example.payments.trade.service.domain.OrderStatus.EXPIRED) {
          expiredSuccessExceptionService.record(order, attempt);
        }
        return;
      }
      String eventId = "PAYMENT_SUCCEEDED:" + attempt.orderId() + ":" + attempt.attemptId();
      String payload =
          paymentSuccessEventSigner.signedPayload(
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
                  order.productCode(),
                  order.amount(),
                  merchantFeeAmount(order),
                  order.currency(),
                  order.orderType().name()));
      outboxRepository.insert(eventId, attempt.orderId(), "PAYMENT_SUCCEEDED", payload);
      merchantNotificationOutboxService.enqueuePaymentSuccess(order);
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
      String productCode,
      java.math.BigDecimal amount,
      java.math.BigDecimal feeAmount,
      String currency,
      String orderType) {}

  static java.math.BigDecimal merchantFeeAmount(PaymentOrder order) {
    return "MERCHANT".equals(order.feeBearer()) ? order.feeAmount() : java.math.BigDecimal.ZERO;
  }

  private static PaymentAttemptStatus statusOf(String value) {
    try {
      return PaymentAttemptStatus.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException exception) {
      return PaymentAttemptStatus.UNKNOWN;
    }
  }
}
