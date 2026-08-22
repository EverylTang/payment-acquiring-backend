package com.example.payments.trade.service.service;

import lombok.RequiredArgsConstructor;

import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OrderService {
  private final PaymentOrderRepository repository;

  @Transactional
  public PaymentOrder create(CreateOrderCommand command) {
    var existing =
        repository
            .findByIdempotency(command.merchantId(), command.idempotencyKey())
            .or(
                () ->
                    repository.findByMerchantOrder(
                        command.merchantId(), command.merchantOrderNo()));
    if (existing.isPresent()) {
      return existing.get();
    }
    PaymentOrder order =
        PaymentOrder.create(
            command.merchantId(),
            command.merchantOrderNo(),
            command.productCode(),
            command.paymentMethod(),
            command.country(),
            command.currency(),
            command.amount(),
            command.idempotencyKey(),
            command.expireAt());
    try {
      return repository.insert(order);
    } catch (DuplicateKeyException duplicate) {
      return repository
          .findByIdempotency(command.merchantId(), command.idempotencyKey())
          .or(() -> repository.findByMerchantOrder(command.merchantId(), command.merchantOrderNo()))
          .orElseThrow(() -> duplicate);
    }
  }

  public PaymentOrder get(String orderId) {
    return repository
        .findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
  }

  public Map<String, Object> list( String merchantId, String status, String currency, int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new IllegalArgumentException("invalid pagination");
    var items =
        repository.search(merchantId, status, currency, page, pageSize).stream()
            .map(com.example.payments.trade.service.controller.OrderDtos.OrderResponse::from)
            .toList();
    return Map.of(
        "items",
        items,
        "page",
        page,
        "pageSize",
        pageSize,
        "total",
        repository.count(merchantId, status, currency));
  }

  public Map<String, Object> statistics() {
    var statistics = repository.statistics();
    BigDecimal successRate =
        statistics.total() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(statistics.successful() * 100.0 / statistics.total())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    return Map.of(
        "totalOrders",
        statistics.total(),
        "successfulOrders",
        statistics.successful(),
        "paymentSuccessRate",
        successRate,
        "paymentVolume",
        statistics.volume(),
        "activeMerchants",
        statistics.merchants());
  }

  @Transactional
  public PaymentOrder markPaying(String orderId) {
    return transition(orderId, OrderStatus.PAYING);
  }

  @Transactional
  public PaymentOrder callback(String orderId, OrderStatus status) {
    if (status == OrderStatus.PAYING || status == OrderStatus.CREATED) {
      transition(orderId, status);
      return get(orderId);
    }
    if (status != OrderStatus.SUCCESS
        && status != OrderStatus.FAILED
        && status != OrderStatus.UNKNOWN
        && status != OrderStatus.CANCELED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "callback status is not allowed");
    }
    PaymentOrder current = get(orderId);
    if (current.status().canTransitionTo(status)) {
      repository.updateStatus(
          orderId, current.status(), status, status == OrderStatus.SUCCESS ? Instant.now() : null);
    }
    return get(orderId);
  }

  @Transactional
  public PaymentOrder cancel(String orderId) {
    PaymentOrder current = get(orderId);
    if (current.status().isTerminal()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "terminal order cannot be canceled");
    }
    if (current.expireAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "expired order cannot be canceled");
    }
    repository.updateStatus(orderId, current.status(), OrderStatus.CANCELED, null);
    return get(orderId);
  }

  private PaymentOrder transition(String orderId, OrderStatus next) {
    PaymentOrder current = get(orderId);
    if (current.status().canTransitionTo(next)) {
      repository.updateStatus(orderId, current.status(), next, null);
    }
    return get(orderId);
  }

  public record CreateOrderCommand(
      String merchantId,
      String merchantOrderNo,
      String productCode,
      String paymentMethod,
      String country,
      String currency,
      java.math.BigDecimal amount,
      String idempotencyKey,
      Instant expireAt) {
    public CreateOrderCommand {
      if (expireAt == null) expireAt = Instant.now().plus(Duration.ofMinutes(30));
    }
  }
}
