package com.example.payments.trade.service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import com.example.payments.trade.service.mapper.PaymentOutboxEventRepository;
import com.example.payments.trade.service.model.PaymentOutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MerchantNotificationDeliveryWorkerTest {
  private final PaymentOutboxEventRepository repository =
      org.mockito.Mockito.mock(PaymentOutboxEventRepository.class);
  private final PaymentOrderRepository orderRepository =
      org.mockito.Mockito.mock(PaymentOrderRepository.class);
  private final MerchantNotificationOutboxService notificationOutbox =
      org.mockito.Mockito.mock(MerchantNotificationOutboxService.class);
  private final MerchantCallbackUrlPolicy callbackUrlPolicy =
      org.mockito.Mockito.mock(MerchantCallbackUrlPolicy.class);
  private final MerchantNotificationSignatureClient signatureClient =
      org.mockito.Mockito.mock(MerchantNotificationSignatureClient.class);
  private final MerchantNotificationHttpClient httpClient =
      org.mockito.Mockito.mock(MerchantNotificationHttpClient.class);
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private MerchantNotificationDeliveryWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new MerchantNotificationDeliveryWorker(
            repository,
            orderRepository,
            notificationOutbox,
            objectMapper,
            callbackUrlPolicy,
            signatureClient,
            httpClient);
    ReflectionTestUtils.setField(worker, "maxAttempts", 3);
    ReflectionTestUtils.setField(worker, "retryBaseSeconds", 5L);
    ReflectionTestUtils.setField(worker, "retryMaxSeconds", 300L);
    ReflectionTestUtils.setField(worker, "maxRetryAgeSeconds", 60L);
  }

  @Test
  void successfulDeliveryMarksOutboxPublished() throws Exception {
    Instant now = Instant.parse("2026-09-05T10:00:00Z");
    PaymentOutboxEventEntity event = event(now);
    PaymentOrder order = successfulOrder();
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(callbackUrlPolicy.validate("https://merchant.example/notify", "notifyUrl"))
        .thenReturn("https://merchant.example/notify");
    when(signatureClient.sign(eq("merchant-1"), any(String.class)))
        .thenReturn(new MerchantNotificationSignatureClient.Signature("key-1", 1L, "nonce", "signature"));
    when(httpClient.post(any(URI.class), eq("event-1"), any(String.class), any()))
        .thenReturn(204);
    when(repository.markPublished("event-1", "claim-1")).thenReturn(true);

    worker.deliver(event, now);

    verify(repository).markPublished("event-1", "claim-1");
    verify(notificationOutbox).delivered(event);
  }

  @Test
  void failedDeliveryUsesExponentialRetryWithoutBlocking() throws Exception {
    Instant now = Instant.parse("2026-09-05T10:00:00Z");
    PaymentOutboxEventEntity event = event(now);
    PaymentOrder order = successfulOrder();
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(callbackUrlPolicy.validate("https://merchant.example/notify", "notifyUrl"))
        .thenReturn("https://merchant.example/notify");
    when(signatureClient.sign(eq("merchant-1"), any(String.class)))
        .thenReturn(new MerchantNotificationSignatureClient.Signature("key-1", 1L, "nonce", "signature"));
    when(httpClient.post(any(URI.class), eq("event-1"), any(String.class), any()))
        .thenReturn(500);
    when(repository.markFailed(
            eq("event-1"),
            eq("claim-1"),
            eq(now.plusSeconds(5)),
            eq("IllegalStateException: merchant notification returned HTTP 500"),
            eq("MERCHANT_NOTIFICATION_DELIVERY"),
            eq(3),
            eq(false)))
        .thenReturn(true);

    worker.deliver(event, now);

    verify(notificationOutbox)
        .deliveryFailed(event, "IllegalStateException: merchant notification returned HTTP 500", 3, false);
  }

  @Test
  void notificationBeyondRetryWindowMovesToDead() throws Exception {
    Instant now = Instant.parse("2026-09-05T10:00:00Z");
    PaymentOutboxEventEntity event = event(now);
    event.setFirstFailedAt(LocalDateTime.ofInstant(now.minusSeconds(60), ZoneOffset.UTC));
    PaymentOrder order = successfulOrder();
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(callbackUrlPolicy.validate("https://merchant.example/notify", "notifyUrl"))
        .thenReturn("https://merchant.example/notify");
    when(signatureClient.sign(eq("merchant-1"), any(String.class)))
        .thenReturn(new MerchantNotificationSignatureClient.Signature("key-1", 1L, "nonce", "signature"));
    when(httpClient.post(any(URI.class), eq("event-1"), any(String.class), any()))
        .thenReturn(500);
    when(repository.markFailed(
            eq("event-1"),
            eq("claim-1"),
            any(Instant.class),
            any(String.class),
            eq("MERCHANT_NOTIFICATION_DELIVERY"),
            eq(3),
            eq(true)))
        .thenReturn(true);

    worker.deliver(event, now);

    verify(notificationOutbox).deliveryFailed(event, "IllegalStateException: merchant notification returned HTTP 500", 3, true);
  }

  private PaymentOutboxEventEntity event(Instant occurredAt) throws Exception {
    PaymentOutboxEventEntity event = new PaymentOutboxEventEntity();
    event.setEventId("event-1");
    event.setAggregateId("order-1");
    event.setEventType(MerchantNotificationOutboxService.EVENT_TYPE);
    event.setClaimToken("claim-1");
    event.setAttemptCount(0);
    event.setPayload(
        objectMapper.writeValueAsString(
            new NotificationPayload(
                "event-1",
                MerchantNotificationOutboxService.EVENT_TYPE,
                occurredAt,
                "order-1",
                "merchant-order-1",
                "SUCCESS",
                new BigDecimal("12.50"),
                "USD",
                occurredAt,
                "https://merchant.example/notify")));
    return event;
  }

  private static PaymentOrder successfulOrder() {
    return PaymentOrder.create(
            "merchant-1",
            "merchant-order-1",
            "product-1",
            "CARD",
            "US",
            "USD",
            new BigDecimal("12.50"),
            "idempotency-1",
            Instant.parse("2026-09-06T10:00:00Z"),
            "https://merchant.example/notify",
            null,
            null,
            null,
            null)
        .withStatus(OrderStatus.SUCCESS, Instant.parse("2026-09-05T10:00:00Z"));
  }

  private record NotificationPayload(
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
}
