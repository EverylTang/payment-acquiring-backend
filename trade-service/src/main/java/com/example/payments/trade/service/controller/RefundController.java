package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.OrderService;
import com.example.payments.trade.service.service.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/orders/{orderId}/refunds")
@RequiredArgsConstructor
public class RefundController {
  private final RefundService service;
  private final OrderService orderService;
  private final GatewayRequestAuthorizer gatewayAuthorizer;

  @PostMapping
  public RefundResponse create(
      @PathVariable String orderId,
      @RequestHeader("X-Merchant-Id") String merchantId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RefundRequest request) {
    gatewayAuthorizer.authorize(gatewayToken);
    owned(orderId, merchantId);
    return RefundResponse.from(
        service.create(orderId, idempotencyKey, request.amount(), request.reason()));
  }

  @GetMapping("/{refundId}")
  public RefundResponse get(
      @PathVariable String refundId,
      @RequestHeader("X-Merchant-Id") String merchantId,
      @RequestHeader("X-Gateway-Token") String gatewayToken) {
    gatewayAuthorizer.authorize(gatewayToken);
    var refund = service.get(refundId);
    owned(refund.getOrderId(), merchantId);
    return RefundResponse.from(refund);
  }

  @PostMapping("/{refundId}/execute")
  public RefundResponse execute(
      @PathVariable String refundId,
      @RequestHeader("X-Merchant-Id") String merchantId,
      @RequestHeader("X-Gateway-Token") String gatewayToken) {
    gatewayAuthorizer.authorize(gatewayToken);
    var refund = service.get(refundId);
    owned(refund.getOrderId(), merchantId);
    return RefundResponse.from(service.execute(refundId));
  }

  @PostMapping("/{refundId}/callback")
  public RefundResponse callback(
      @PathVariable String refundId,
      @RequestHeader("X-Callback-Id") String callbackId,
      @RequestHeader("X-Callback-Signature") String signature,
      @RequestHeader("X-Callback-Timestamp") long timestamp,
      @RequestHeader("X-Callback-Nonce") String nonce,
      @Valid @RequestBody CallbackRequest request) {
    return RefundResponse.from(
        service.callback(
            callbackId,
            refundId,
            request.status(),
            request.payload(),
            signature,
            timestamp,
            nonce));
  }

  public record RefundRequest(
      @NotNull @DecimalMin("0.01") BigDecimal amount, @NotBlank String reason) {}

  public record CallbackRequest(@NotBlank String status, @NotBlank String payload) {}

  private void owned(String orderId, String merchantId) {
    var order = orderService.get(orderId);
    if (!merchantId.equals(order.merchantId())) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "order not found");
    }
  }

  public record RefundResponse(
      String refundId,
      String orderId,
      BigDecimal amount,
      String currency,
      String status,
      String reason) {
    static RefundResponse from(com.example.payments.trade.service.model.PaymentRefundEntity value) {
      return new RefundResponse(
          value.getRefundId(),
          value.getOrderId(),
          value.getAmount(),
          value.getCurrency(),
          value.getStatus(),
          value.getReason());
    }
  }
}
