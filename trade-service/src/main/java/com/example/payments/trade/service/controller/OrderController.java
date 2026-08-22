package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.service.OrderService;
import com.example.payments.trade.service.service.PaymentAttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/payments/orders")
public class OrderController {
  private final OrderService orderService;
  private final PaymentAttemptService paymentAttemptService;

  public OrderController(OrderService orderService, PaymentAttemptService paymentAttemptService) {
    this.orderService = orderService;
    this.paymentAttemptService = paymentAttemptService;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("service", "trade-service", "status", "UP", "time", Instant.now().toString());
  }

  @PostMapping
  public OrderDtos.OrderResponse create(
      @Valid @RequestBody OrderDtos.CreateOrderRequest request,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
    }
    var order =
        orderService.create(
            new OrderService.CreateOrderCommand(
                request.merchantId(),
                request.merchantOrderNo(),
                request.productCode(),
                request.paymentMethod(),
                request.country(),
                request.currency(),
                request.amount(),
                idempotencyKey,
                request.expireAt()));
    return OrderDtos.OrderResponse.from(order);
  }

  @GetMapping("/{orderId}")
  public OrderDtos.OrderResponse get(@PathVariable(name = "orderId") String orderId) {
    return OrderDtos.OrderResponse.from(orderService.get(orderId));
  }

  @GetMapping("/{orderId}/status")
  public Map<String, String> status(@PathVariable(name = "orderId") String orderId) {
    return Map.of("orderId", orderId, "status", orderService.get(orderId).status().name());
  }

  @PostMapping("/{orderId}/cancel")
  public OrderDtos.OrderResponse cancel(@PathVariable(name = "orderId") String orderId) {
    return OrderDtos.OrderResponse.from(orderService.cancel(orderId));
  }

  @PostMapping("/{orderId}/attempts")
  public Map<String, Object> createAttempt(
      @PathVariable(name = "orderId") String orderId,
      @RequestParam(name = "behavior", required = false) String behavior) {
    var order = orderService.markPaying(orderId);
    var attempt = paymentAttemptService.create(order, behavior);
    return Map.of(
        "attemptId",
        attempt.attemptId(),
        "orderId",
        attempt.orderId(),
        "channelId",
        attempt.channelId(),
        "channelOrderId",
        attempt.channelRequestNo(),
        "status",
        attempt.status().name(),
        "responseSnapshot",
        attempt.responseSnapshot());
  }

  @PostMapping("/{orderId}/callback")
  public OrderDtos.OrderResponse callback(
      @PathVariable(name = "orderId") String orderId,
      @RequestParam(name = "status") @NotBlank String status) {
    try {
      return OrderDtos.OrderResponse.from(
          orderService.callback(orderId, OrderStatus.valueOf(status.toUpperCase())));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported status", exception);
    }
  }

  @GetMapping("/{orderId}/attempts/{attemptId}")
  public Map<String, Object> getAttempt(
      @PathVariable String orderId, @PathVariable String attemptId) {
    var attempt = paymentAttemptService.get(attemptId, orderId);
    return attemptResponse(attempt);
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/query")
  public Map<String, Object> queryAttempt(
      @PathVariable String orderId, @PathVariable String attemptId) {
    return attemptResponse(
        paymentAttemptService.query(paymentAttemptService.get(attemptId, orderId).attemptId()));
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/cancel")
  public Map<String, Object> cancelAttempt(
      @PathVariable String orderId, @PathVariable String attemptId) {
    paymentAttemptService.get(attemptId, orderId);
    return attemptResponse(paymentAttemptService.cancel(attemptId));
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/retry")
  public Map<String, Object> retryAttempt(
      @PathVariable String orderId, @PathVariable String attemptId) {
    var order = orderService.get(orderId);
    return attemptResponse(paymentAttemptService.retry(attemptId, order));
  }

  private static Map<String, Object> attemptResponse(
      com.example.payments.trade.service.domain.PaymentAttempt attempt) {
    return Map.of(
        "attemptId",
        attempt.attemptId(),
        "orderId",
        attempt.orderId(),
        "channelId",
        attempt.channelId(),
        "channelOrderId",
        attempt.channelRequestNo(),
        "attemptNo",
        attempt.attemptNo(),
        "status",
        attempt.status().name(),
        "responseSnapshot",
        attempt.responseSnapshot() == null ? "" : attempt.responseSnapshot());
  }

  @PostMapping("/attempts/callback")
  public Map<String, Object> attemptCallback(@Valid @RequestBody CallbackRequest request) {
    var attempt =
        paymentAttemptService.callback(
            request.rawPayload(), request.signature(), request.callbackId());
    return Map.of(
        "attemptId",
        attempt.attemptId(),
        "orderId",
        attempt.orderId(),
        "status",
        attempt.status().name(),
        "responseSnapshot",
        attempt.responseSnapshot());
  }

  public record CallbackRequest(
      @NotBlank String callbackId, @NotBlank String rawPayload, @NotBlank String signature) {}
}
