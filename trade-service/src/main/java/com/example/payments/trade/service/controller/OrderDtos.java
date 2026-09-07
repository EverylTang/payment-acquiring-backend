package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.domain.PaymentOrder;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class OrderDtos {
  private OrderDtos() {}

  public record CreateOrderRequest(
      @NotBlank @Size(max = 128) String merchantOrderNo,
      @NotBlank @Size(min = 4, max = 32) @Pattern(regexp = "[0-9]{4,}") String appId,
      @NotBlank @Size(max = 64) String payModel,
      @Size(min = 2, max = 2) @Pattern(regexp = "[A-Za-z]{2}") String country,
      @NotBlank @Size(min = 3, max = 3) @Pattern(regexp = "[A-Za-z]{3}") String currency,
      @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 4) BigDecimal amount,
      Instant expireAt,
      String notifyUrl,
      String returnUrl,
      @Size(max = 128) String customerReference,
      @Size(max = 128) String payoutDestinationRef,
      @Size(max = 1000) String description,
      Payer payer,
      java.util.Map<String, Object> channelParams) {
    public CreateOrderRequest {
      channelParams = channelParams == null ? java.util.Map.of() : java.util.Map.copyOf(channelParams);
    }
  }

  /** Channel-required payer data. It is never returned by merchant order projections. */
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
