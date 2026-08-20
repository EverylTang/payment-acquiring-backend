package com.example.payments.trade.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentOrder(
    String orderId,
    String merchantId,
    String merchantOrderNo,
    String productCode,
    String paymentMethod,
    String country,
    String currency,
    BigDecimal amount,
    BigDecimal feeAmount,
    BigDecimal netAmount,
    OrderStatus status,
    String idempotencyKey,
    String routeSnapshot,
    String pricingSnapshot,
    Instant expireAt,
    Instant createdAt,
    Instant paidAt,
    String paymentToken) {

  public static PaymentOrder create(
      String merchantId,
      String merchantOrderNo,
      String productCode,
      String paymentMethod,
      String country,
      String currency,
      BigDecimal amount,
      String idempotencyKey,
      Instant expireAt) {
    BigDecimal fee = amount.multiply(new BigDecimal("0.0200")).setScale(2);
    return new PaymentOrder(
        UUID.randomUUID().toString(), merchantId, merchantOrderNo, productCode, paymentMethod,
        country, currency, amount, fee, amount.subtract(fee), OrderStatus.CREATED, idempotencyKey,
        "{\"route\":\"simulated-channel\",\"version\":1}",
        "{\"feeRate\":\"0.0200\",\"mode\":\"INCLUSIVE\",\"version\":1}",
        expireAt, Instant.now(), null, "simulated-token-" + UUID.randomUUID());
  }

  public PaymentOrder withStatus(OrderStatus nextStatus, Instant paymentTime) {
    if (status.isTerminal() && status != nextStatus) {
      return this;
    }
    return new PaymentOrder(orderId, merchantId, merchantOrderNo, productCode, paymentMethod,
        country, currency, amount, feeAmount, netAmount, nextStatus, idempotencyKey,
        routeSnapshot, pricingSnapshot, expireAt, createdAt,
        nextStatus == OrderStatus.SUCCESS ? paymentTime : paidAt, paymentToken);
  }
}
