package com.example.payments.trade.service.service;

import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import com.example.payments.trade.service.mapper.PaymentOutboxEventRepository;
import com.example.payments.trade.service.model.PaymentOutboxEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Creates and tracks merchant notification events without changing payment status. */
@Service
@RequiredArgsConstructor
public class MerchantNotificationOutboxService {
  public static final String EVENT_TYPE = "MERCHANT_PAYMENT_NOTIFICATION";

  private final PaymentOrderRepository orderRepository;
  private final PaymentOutboxEventRepository outboxRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public void enqueuePaymentSuccess(PaymentOrder order) {
    if (order.notifyUrl() == null || order.notifyUrl().isBlank()) return;
    enqueue(order, EVENT_TYPE + ":" + order.orderId(), null, null);
  }

  @Transactional
  public PaymentOrder resend(String orderId, String operator, String reason, String requestId) {
    if (reason == null || reason.isBlank() || reason.length() > 512) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "notification reason is required");
    }
    var order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    if (order.status() != OrderStatus.SUCCESS) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "only successful orders can be notified");
    }
    if (order.notifyUrl() == null || order.notifyUrl().isBlank()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "order has no notify URL");
    }
    String eventId = EVENT_TYPE + ":" + order.orderId() + ":MANUAL:" + UUID.randomUUID();
    enqueue(order, eventId, operator, reason);
    outboxRepository.insertAudit(eventId, operator, reason, order.callbackStatus(), "PENDING", requestId, Instant.now());
    return orderRepository.findById(orderId).orElse(order);
  }

  public void published(PaymentOutboxEventEntity event) {
    if (!EVENT_TYPE.equals(event.getEventType())) return;
    orderRepository.updateCallbackState(
        event.getAggregateId(), "PUBLISHED", event.getEventId(),
        event.getAttemptCount() == null ? 0 : event.getAttemptCount(), Instant.now(), null);
  }

  public void failed(PaymentOutboxEventEntity event, String error, int maxAttempts) {
    if (!EVENT_TYPE.equals(event.getEventType())) return;
    int attempts = (event.getAttemptCount() == null ? 0 : event.getAttemptCount()) + 1;
    orderRepository.updateCallbackState(
        event.getAggregateId(), attempts >= maxAttempts ? "FAILED" : "RETRYING", event.getEventId(),
        attempts, null, error);
  }

  public void delivered(String orderId, String eventId, int attempts) {
    orderRepository.updateCallbackState(orderId, "DELIVERED", eventId, attempts, Instant.now(), null);
  }

  public void deliveryFailed(String orderId, String eventId, int attempts, String error) {
    orderRepository.updateCallbackState(orderId, "FAILED", eventId, attempts, null, error);
  }

  private void enqueue(PaymentOrder order, String eventId, String operator, String reason) {
    try {
      String payload =
          objectMapper.writeValueAsString(
              new MerchantPaymentNotification(
                  eventId,
                  order.orderId(),
                  order.merchantId(),
                  order.merchantOrderNo(),
                  order.status().name(),
                  order.amount(),
                  order.currency(),
                  order.paidAt(),
                  order.notifyUrl()));
      if (outboxRepository.insert(eventId, order.orderId(), EVENT_TYPE, payload)) {
        orderRepository.updateCallbackState(order.orderId(), "PENDING", eventId, 0, null, null);
      }
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("merchant notification event serialization failed", exception);
    }
  }

  private record MerchantPaymentNotification(
      String eventId,
      String orderId,
      String merchantId,
      String merchantOrderNo,
      String paymentStatus,
      java.math.BigDecimal amount,
      String currency,
      Instant paidAt,
      String notifyUrl) {}
}
