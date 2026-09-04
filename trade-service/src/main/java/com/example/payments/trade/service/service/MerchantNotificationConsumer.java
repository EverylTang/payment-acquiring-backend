package com.example.payments.trade.service.service;

import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Delivers merchant notifications after their outbox events have reached the message bus. */
@Component
@RocketMQMessageListener(
    topic = MerchantNotificationOutboxService.EVENT_TYPE,
    consumerGroup = "${trade.merchant-notification.consumer-group:trade-merchant-notification}",
    maxReconsumeTimes = 0)
@RequiredArgsConstructor
public class MerchantNotificationConsumer implements RocketMQListener<String> {
  private static final int MAX_RETRIES = 3;
  private static final int MAX_DELIVERY_ATTEMPTS = 1 + MAX_RETRIES;

  private final PaymentOrderRepository orderRepository;
  private final MerchantNotificationOutboxService notificationOutboxService;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Value("${trade.merchant-notification.retry-interval-ms:1000}")
  private long retryIntervalMs;

  @Override
  public void onMessage(String message) {
    Notification event = parse(message);
    var order = orderRepository.findById(event.orderId()).orElseThrow();
    if (order.notifyUrl() == null || !order.notifyUrl().equals(event.notifyUrl())) {
      notificationOutboxService.deliveryFailed(
          event.orderId(), event.eventId(), 0, "notification URL does not match order snapshot");
      return;
    }
    String error = null;
    for (int attempt = 1; attempt <= MAX_DELIVERY_ATTEMPTS; attempt++) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder(URI.create(event.notifyUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Payment-Notification-Id", event.eventId())
                .POST(HttpRequest.BodyPublishers.ofString(message))
                .build();
        HttpResponse<Void> response =
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          notificationOutboxService.delivered(event.orderId(), event.eventId(), attempt);
          return;
        }
        error = "merchant notification returned HTTP " + response.statusCode();
      } catch (Exception exception) {
        error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
      }
      if (attempt < MAX_DELIVERY_ATTEMPTS) waitBeforeRetry();
    }
    notificationOutboxService.deliveryFailed(event.orderId(), event.eventId(), MAX_DELIVERY_ATTEMPTS, error);
  }

  private Notification parse(String message) {
    try {
      JsonNode event = objectMapper.readTree(message);
      return new Notification(
          required(event, "eventId"), required(event, "orderId"), required(event, "notifyUrl"));
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid merchant notification event", exception);
    }
  }

  private void waitBeforeRetry() {
    try {
      Thread.sleep(retryIntervalMs);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("merchant notification retry interrupted", exception);
    }
  }

  private static String required(JsonNode event, String field) {
    String value = event.path(field).asText();
    if (value.isBlank()) throw new IllegalArgumentException("missing " + field);
    return value;
  }

  private record Notification(String eventId, String orderId, String notifyUrl) {}
}
