package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.OrderService;
import com.example.payments.trade.service.service.RefundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Administrative refund operations use the existing order-management permission. */
@RestController
@RequestMapping("/api/admin/v1/orders/{orderId}/refunds")
@RequiredArgsConstructor
public class AdminRefundController {
  private final RefundService refundService;
  private final OrderService orderService;
  private final AdminRequestAuthorizer authorizer;

  @PostMapping
  public RefundController.RefundResponse create(
      @PathVariable String orderId,
      @Valid @RequestBody RefundRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:manage");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
    }
    orderService.get(orderId);
    return RefundController.RefundResponse.from(
        refundService.create(orderId, idempotencyKey, request.amount(), request.reason()));
  }

  @GetMapping("/{refundId}")
  public RefundController.RefundResponse get(
      @PathVariable String orderId,
      @PathVariable String refundId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:list");
    return responseForOrder(orderId, refundId);
  }

  @PostMapping("/{refundId}/execute")
  public RefundController.RefundResponse execute(
      @PathVariable String orderId,
      @PathVariable String refundId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:manage");
    responseForOrder(orderId, refundId);
    return RefundController.RefundResponse.from(refundService.execute(refundId));
  }

  private RefundController.RefundResponse responseForOrder(String orderId, String refundId) {
    var refund = refundService.get(refundId);
    if (!orderId.equals(refund.getOrderId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "refund not found");
    }
    return RefundController.RefundResponse.from(refund);
  }

  public record RefundRequest(
      @NotNull @jakarta.validation.constraints.DecimalMin("0.01") BigDecimal amount,
      @jakarta.validation.constraints.NotBlank String reason) {}
}
