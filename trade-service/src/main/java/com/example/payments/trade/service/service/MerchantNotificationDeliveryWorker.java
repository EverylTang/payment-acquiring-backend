package com.example.payments.trade.service.service;

import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import com.example.payments.trade.service.mapper.PaymentOutboxEventRepository;
import com.example.payments.trade.service.model.PaymentOutboxEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Delivers merchant notifications from the durable outbox without occupying a message consumer. */
@Component
@RequiredArgsConstructor
public class MerchantNotificationDeliveryWorker {
  private final PaymentOutboxEventRepository outboxRepository;
  private final PaymentOrderRepository orderRepository;
  private final MerchantNotificationOutboxService notificationOutboxService;
  private final ObjectMapper objectMapper;
  private final MerchantCallbackUrlPolicy callbackUrlPolicy;
  private final MerchantNotificationSignatureClient signatureClient;
  private final MerchantNotificationHttpClient httpClient;

  @Value("${trade.outbox.batch-size:50}")
  private int batchSize;

  @Value("${trade.merchant-notification.max-attempts:8}")
  private int maxAttempts;

  @Value("${trade.merchant-notification.retry-base-seconds:5}")
  private long retryBaseSeconds;

  @Value("${trade.merchant-notification.retry-max-seconds:300}")
  private long retryMaxSeconds;

  @Value("${trade.merchant-notification.max-retry-age-seconds:86400}")
  private long maxRetryAgeSeconds;

  @Value("${trade.merchant-notification.claim-timeout-seconds:60}")
  private long claimTimeoutSeconds;

  @Scheduled(fixedDelayString = "${trade.merchant-notification.delivery-ms:1000}")
  public void deliverPending() {
    Instant now = Instant.now();
    for (PaymentOutboxEventEntity event :
        outboxRepository.claimMerchantNotifications(now, batchSize, claimTimeoutSeconds)) {
      deliver(event, now);
    }
  }

  void deliver(PaymentOutboxEventEntity event, Instant now) {
    try {
      Notification notification = parse(event.getPayload());
      if (!MerchantNotificationOutboxService.EVENT_TYPE.equals(notification.eventType())) {
        throw new IllegalArgumentException("unexpected merchant notification event type");
      }
      var order = orderRepository.findById(notification.orderId()).orElseThrow();
      if (order.notifyUrl() == null || !order.notifyUrl().equals(notification.notifyUrl())) {
        throw new IllegalArgumentException("notification URL does not match order snapshot");
      }
      String body = publicPayload(notification);
      var signature = signatureClient.sign(order.merchantId(), body);
      int statusCode =
          httpClient.post(
              URI.create(callbackUrlPolicy.validate(notification.notifyUrl(), "notifyUrl")),
              notification.eventId(),
              body,
              signature);
      if (statusCode < 200 || statusCode >= 300) {
        throw new IllegalStateException("merchant notification returned HTTP " + statusCode);
      }
      if (outboxRepository.markPublished(event.getEventId(), event.getClaimToken())) {
        notificationOutboxService.delivered(event);
      }
    } catch (Exception exception) {
      scheduleRetry(event, now, errorMessage(exception));
    }
  }

  private void scheduleRetry(PaymentOutboxEventEntity event, Instant now, String error) {
    int attemptCount = event.getAttemptCount() == null ? 0 : event.getAttemptCount();
    boolean expired = retryWindowExpired(event, now);
    long delay = Math.min(retryMaxSeconds, retryBaseSeconds * (1L << Math.min(attemptCount, 30)));
    if (outboxRepository.markFailed(
        event.getEventId(),
        event.getClaimToken(),
        now.plusSeconds(delay),
        error,
        "MERCHANT_NOTIFICATION_DELIVERY",
        maxAttempts,
        expired)) {
      notificationOutboxService.deliveryFailed(event, error, maxAttempts, expired);
    }
  }

  private boolean retryWindowExpired(PaymentOutboxEventEntity event, Instant now) {
    if (event.getFirstFailedAt() == null) return false;
    Instant deadline =
        event.getFirstFailedAt().toInstant(ZoneOffset.UTC).plusSeconds(maxRetryAgeSeconds);
    return !now.isBefore(deadline);
  }

  private Notification parse(String payload) {
    try {
      JsonNode event = objectMapper.readTree(payload);
      return new Notification(
          required(event, "eventId"),
          required(event, "eventType"),
          Instant.parse(required(event, "occurredAt")),
          required(event, "orderId"),
          required(event, "merchantOrderNo"),
          required(event, "paymentStatus"),
          event.required("amount").decimalValue(),
          required(event, "currency"),
          event.path("paidAt").isNull() ? null : Instant.parse(required(event, "paidAt")),
          required(event, "notifyUrl"));
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid merchant notification event", exception);
    }
  }

  private String publicPayload(Notification notification) {
    try {
      return objectMapper.writeValueAsString(
          new PublicNotification(
              notification.eventId(),
              notification.eventType(),
              notification.occurredAt(),
              notification.orderId(),
              notification.merchantOrderNo(),
              notification.paymentStatus(),
              notification.amount(),
              notification.currency(),
              notification.paidAt()));
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("merchant notification serialization failed", exception);
    }
  }

  private static String required(JsonNode event, String field) {
    String value = event.path(field).asText();
    if (value.isBlank()) throw new IllegalArgumentException("missing " + field);
    return value;
  }

  private static String errorMessage(Exception exception) {
    String message = exception.getMessage();
    return exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
  }

  private record Notification(
      String eventId,
      String eventType,
      Instant occurredAt,
      String orderId,
      String merchantOrderNo,
      String paymentStatus,
      BigDecimal amount,
      String currency,
      Instant paidAt,
      String notifyUrl) {}

  private record PublicNotification(
      String eventId,
      String eventType,
      Instant occurredAt,
      String orderId,
      String merchantOrderNo,
      String status,
      BigDecimal amount,
      String currency,
      Instant paidAt) {}
}
