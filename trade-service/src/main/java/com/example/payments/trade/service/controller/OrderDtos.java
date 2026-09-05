package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.domain.PaymentAttempt;
import com.example.payments.trade.service.domain.PaymentOrder;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class OrderDtos {
  private OrderDtos() {}

  public record CreateOrderRequest(
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
      String payoutDestinationRef,
      String description,
      Payer payer) {}

  /** Channel-required payer data. It is never returned by merchant order or attempt projections. */
  public record Payer(
      String userId, String name, String firstName, String lastName, String phone, String email) {
    public java.util.Map<String, String> asMap() {
      var values = new java.util.LinkedHashMap<String, String>();
      values.put("userId", userId);
      values.put("name", name);
      values.put("firstName", firstName);
      values.put("lastName", lastName);
      values.put("phone", phone);
      values.put("email", email);
      values.values().removeIf(value -> value == null || value.isBlank());
      return java.util.Map.copyOf(values);
    }
  }

  /** Merchant-facing order projection. Internal snapshots and operational state stay admin-only. */
  public record MerchantOrderResponse(
      String orderId,
      String merchantOrderNo,
      BigDecimal amount,
      String currency,
      String status,
      Instant expireAt,
      Instant paidAt) {
    public static MerchantOrderResponse from(PaymentOrder order) {
      return new MerchantOrderResponse(
          order.orderId(),
          order.merchantOrderNo(),
          order.amount(),
          order.currency(),
          order.status().name(),
          order.expireAt(),
          order.paidAt());
    }
  }

  /**
   * Merchant-facing attempt projection. Channel snapshots are retained for protected operations.
   */
  public record MerchantAttemptResponse(
      String attemptId,
      String orderId,
      String channelOrderId,
      String status,
      String failureCode,
      String paymentUrl,
      String qrCode) {
    public static MerchantAttemptResponse from(PaymentAttempt attempt) {
      return new MerchantAttemptResponse(
          attempt.attemptId(),
          attempt.orderId(),
          attempt.channelRequestNo(),
          attempt.status().name(),
          attempt.failureCode(),
          attempt.paymentUrl(),
          attempt.qrCode());
    }
  }

  public record OrderResponse(
      String orderId,
      String merchantId,
      String merchantOrderNo,
      String productCode,
      String orderType,
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
      String channelId,
      String channelOrderId,
      String channelStatus,
      String channelResponseSnapshot,
      String merchantRequestSnapshot,
      String routeSnapshot,
      String pricingSnapshot,
      Instant expireAt,
      Instant createdAt,
      Instant paidAt,
      String notifyUrl,
      String returnUrl,
      String customerReference,
      String payoutDestinationRef,
      String description,
      String callbackStatus,
      String callbackEventId,
      Integer callbackAttemptCount,
      Instant callbackLastNotifiedAt,
      String callbackLastError) {
    public static OrderResponse from(PaymentOrder order) {
      return from(order, null);
    }

    public static OrderResponse from(PaymentOrder order, PaymentAttempt channel) {
      return new OrderResponse(
          order.orderId(),
          order.merchantId(),
          order.merchantOrderNo(),
          order.productCode(),
          order.orderType().name(),
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
          channel == null ? null : channel.channelId(),
          channel == null ? null : channel.channelRequestNo(),
          channel == null ? null : channel.status().name(),
          channel == null ? null : channel.responseSnapshot(),
          order.merchantRequestSnapshot(),
          order.routeSnapshot(),
          order.pricingSnapshot(),
          order.expireAt(),
          order.createdAt(),
          order.paidAt(),
          order.notifyUrl(),
          order.returnUrl(),
          order.customerReference(),
          order.payoutDestinationRef(),
          order.description(),
          order.callbackStatus(),
          order.callbackEventId(),
          order.callbackAttemptCount(),
          order.callbackLastNotifiedAt(),
          order.callbackLastError());
    }
  }
}
