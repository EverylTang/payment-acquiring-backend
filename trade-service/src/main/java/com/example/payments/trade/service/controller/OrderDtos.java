package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.domain.PaymentOrder;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class OrderDtos {
  private OrderDtos() {}

  public record CreateOrderRequest(
      @NotBlank String merchantId,
      @NotBlank String merchantOrderNo,
      @NotBlank String productCode,
      @NotBlank String paymentMethod,
      String country,
      @NotBlank String currency,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      Instant expireAt) {}

  public record OrderResponse(
      String orderId, String merchantId, String merchantOrderNo, String currency,
      BigDecimal amount, BigDecimal feeAmount, BigDecimal netAmount, String status,
      String paymentToken, String routeSnapshot, String pricingSnapshot,
      Instant expireAt, Instant createdAt, Instant paidAt) {
    public static OrderResponse from(PaymentOrder order) {
      return new OrderResponse(order.orderId(), order.merchantId(), order.merchantOrderNo(),
          order.currency(), order.amount(), order.feeAmount(), order.netAmount(),
          order.status().name(), order.paymentToken(), order.routeSnapshot(), order.pricingSnapshot(),
          order.expireAt(), order.createdAt(), order.paidAt());
    }
  }
}
