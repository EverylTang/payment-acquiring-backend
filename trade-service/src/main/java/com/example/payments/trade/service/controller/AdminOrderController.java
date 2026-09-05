package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.OrderService;
import com.example.payments.trade.service.service.MerchantNotificationOutboxService;
import com.example.payments.trade.service.service.PaymentAttemptService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/v1/orders")
@RequiredArgsConstructor
public class AdminOrderController {
  private final OrderService orderService;
  private final PaymentAttemptService paymentAttemptService;
  private final MerchantNotificationOutboxService merchantNotificationOutboxService;
  private final AdminRequestAuthorizer authorizer;

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String merchantId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String orderType,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:list");
    return orderService.list(merchantId, status, currency, orderType, page, pageSize);
  }

  @GetMapping("/statistics")
  public Map<String, Object> statistics(
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:statistics");
    return orderService.statistics();
  }

  @GetMapping("/{orderId}")
  public OrderDtos.OrderResponse get(
      @PathVariable String orderId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:list");
    var order = orderService.get(orderId);
    return OrderDtos.OrderResponse.from(
        order, paymentAttemptService.latestForOrder(order.orderId()).orElse(null));
  }

  @PostMapping
  public OrderDtos.OrderResponse create(
      @Valid @RequestBody AdminCreateOrderRequest request,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:manage");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
    }
    return OrderDtos.OrderResponse.from(
        orderService.create(
            new OrderService.CreateOrderCommand(
                request.merchantId(), request.merchantOrderNo(), request.productCode(),
                request.paymentMethod(), request.country(), request.currency(), request.amount(),
                idempotencyKey, request.expireAt(), request.notifyUrl(), request.returnUrl(),
                request.customerReference(), request.payoutDestinationRef(), request.description())));
  }

  @PostMapping("/{orderId}/cancel")
  public OrderDtos.OrderResponse cancel(
      @PathVariable String orderId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:manage");
    return OrderDtos.OrderResponse.from(orderService.cancel(orderId));
  }

  @PostMapping("/{orderId}/notifications")
  public OrderDtos.OrderResponse resendNotification(
      @PathVariable String orderId,
      @Valid @RequestBody ResendNotificationRequest request,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions,
      @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:notify");
    return OrderDtos.OrderResponse.from(
        merchantNotificationOutboxService.resend(orderId, operator, request.reason(), requestId));
  }

  @PostMapping("/{orderId}/attempts")
  public Map<String, Object> createAttempt(
      @PathVariable String orderId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:manage");
    var attempt = paymentAttemptService.create(orderService.markPaying(orderId));
    return attemptResponse(attempt);
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/query")
  public Map<String, Object> queryAttempt(
      @PathVariable String orderId, @PathVariable String attemptId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:manage");
    return attemptResponse(paymentAttemptService.query(paymentAttemptService.get(attemptId, orderId).attemptId()));
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/cancel")
  public Map<String, Object> cancelAttempt(
      @PathVariable String orderId, @PathVariable String attemptId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:manage");
    paymentAttemptService.get(attemptId, orderId);
    return attemptResponse(paymentAttemptService.cancel(attemptId));
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/retry")
  public Map<String, Object> retryAttempt(
      @PathVariable String orderId, @PathVariable String attemptId,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:manage");
    return attemptResponse(paymentAttemptService.retry(attemptId, orderService.get(orderId)));
  }

  private static Map<String, Object> attemptResponse(
      com.example.payments.trade.service.domain.PaymentAttempt attempt) {
    return Map.of("attemptId", attempt.attemptId(), "orderId", attempt.orderId(),
        "channelId", attempt.channelId(), "channelOrderId", attempt.channelRequestNo(),
        "attemptNo", attempt.attemptNo(), "status", attempt.status().name(),
        "requestSnapshot", attempt.requestSnapshot() == null ? "" : attempt.requestSnapshot(),
        "responseSnapshot", attempt.responseSnapshot() == null ? "" : attempt.responseSnapshot());
  }

  public record ResendNotificationRequest(@jakarta.validation.constraints.NotBlank String reason) {}

  public record AdminCreateOrderRequest(
      @jakarta.validation.constraints.NotBlank String merchantId,
      @jakarta.validation.constraints.NotBlank String merchantOrderNo,
      @jakarta.validation.constraints.NotBlank String productCode,
      @jakarta.validation.constraints.NotBlank String paymentMethod,
      String country,
      @jakarta.validation.constraints.NotBlank String currency,
      @jakarta.validation.constraints.NotNull
          @jakarta.validation.constraints.DecimalMin("0.01") java.math.BigDecimal amount,
      java.time.Instant expireAt,
      String notifyUrl,
      String returnUrl,
      String customerReference,
      String payoutDestinationRef,
      String description) {}
}
