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
      Instant expireAt,
      String notifyUrl,
      String returnUrl,
      String customerReference,
      String description) {}

  public record OrderResponse(
      String orderId,
      String merchantId,
      String merchantOrderNo,
      String productCode,
      String paymentMethod,
      String country,
      String currency,
      BigDecimal amount,
      BigDecimal feeAmount,
      BigDecimal payerPayableAmount,
      BigDecimal netAmount,
      String feeBearer,
      String status,
      String paymentToken,
      String routeSnapshot,
      String pricingSnapshot,
      Instant expireAt,
      Instant createdAt,
      Instant paidAt,
      String notifyUrl,
      String returnUrl,
      String customerReference,
      String description,
      String callbackStatus,
      String callbackEventId,
      Integer callbackAttemptCount,
      Instant callbackLastNotifiedAt,
      String callbackLastError) {
    public static OrderResponse from(PaymentOrder order) {
      return new OrderResponse(
          order.orderId(),
          order.merchantId(),
          order.merchantOrderNo(),
          order.productCode(),
          order.paymentMethod(),
          order.country(),
          order.currency(),
          order.amount(),
          order.feeAmount(),
          order.payerPayableAmount(),
          order.netAmount(),
          order.feeBearer(),
          order.status().name(),
          order.paymentToken(),
          order.routeSnapshot(),
          order.pricingSnapshot(),
          order.expireAt(),
          order.createdAt(),
          order.paidAt(),
          order.notifyUrl(),
          order.returnUrl(),
          order.customerReference(),
          order.description(),
          order.callbackStatus(),
          order.callbackEventId(),
          order.callbackAttemptCount(),
          order.callbackLastNotifiedAt(),
          order.callbackLastError());
    }
  }
}
