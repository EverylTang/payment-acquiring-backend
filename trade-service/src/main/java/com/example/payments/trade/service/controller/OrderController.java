package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.OrderService;
import com.example.payments.trade.service.service.PaymentAttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
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

@RestController
@RequestMapping("/api/v1/payments/orders")
@RequiredArgsConstructor
public class OrderController {
  private final OrderService orderService;
  private final PaymentAttemptService paymentAttemptService;

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("service", "trade-service", "status", "UP", "time", Instant.now().toString());
  }

  @PostMapping
  public OrderDtos.MerchantOrderResponse create(
      @Valid @RequestBody OrderDtos.CreateOrderRequest request,
      @RequestHeader("X-Merchant-Id") String merchantId,
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is required");
    }
    var order =
        orderService.create(
            new OrderService.CreateOrderCommand(
                merchantId,
                request.merchantOrderNo(),
                request.productCode(),
                request.paymentMethod(),
                request.country(),
                request.currency(),
                request.amount(),
                idempotencyKey,
                request.expireAt(),
                request.notifyUrl(),
                request.returnUrl(),
                request.customerReference(),
                request.payoutDestinationRef(),
                request.description(),
                request.payer() == null ? java.util.Map.of() : request.payer().asMap()));
    return OrderDtos.MerchantOrderResponse.from(order);
  }

  @GetMapping("/{orderId}")
  public OrderDtos.MerchantOrderResponse get(
      @PathVariable(name = "orderId") String orderId,
      @RequestHeader("X-Merchant-Id") String merchantId) {
    return OrderDtos.MerchantOrderResponse.from(owned(orderId, merchantId));
  }

  @GetMapping("/{orderId}/status")
  public Map<String, String> status(
      @PathVariable(name = "orderId") String orderId,
      @RequestHeader("X-Merchant-Id") String merchantId) {
    return Map.of("orderId", orderId, "status", owned(orderId, merchantId).status().name());
  }

  @PostMapping("/{orderId}/cancel")
  public OrderDtos.MerchantOrderResponse cancel(
      @PathVariable(name = "orderId") String orderId,
      @RequestHeader("X-Merchant-Id") String merchantId) {
    owned(orderId, merchantId);
    return OrderDtos.MerchantOrderResponse.from(orderService.cancel(orderId));
  }

  @PostMapping("/{orderId}/attempts")
  public OrderDtos.MerchantAttemptResponse createAttempt(
      @PathVariable(name = "orderId") String orderId,
      @RequestHeader("X-Merchant-Id") String merchantId) {
    owned(orderId, merchantId);
    var attempt = paymentAttemptService.create(orderService.requireActive(orderId));
    return OrderDtos.MerchantAttemptResponse.from(attempt);
  }

  @GetMapping("/{orderId}/attempts/{attemptId}")
  public OrderDtos.MerchantAttemptResponse getAttempt(
      @PathVariable String orderId,
      @PathVariable String attemptId,
      @RequestHeader("X-Merchant-Id") String merchantId) {
    owned(orderId, merchantId);
    var attempt = paymentAttemptService.get(attemptId, orderId);
    return attemptResponse(attempt);
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/query")
  public OrderDtos.MerchantAttemptResponse queryAttempt(
      @PathVariable String orderId,
      @PathVariable String attemptId,
      @RequestHeader("X-Merchant-Id") String merchantId) {
    owned(orderId, merchantId);
    return attemptResponse(
        paymentAttemptService.requestQuery(
            paymentAttemptService.get(attemptId, orderId).attemptId()));
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/cancel")
  public OrderDtos.MerchantAttemptResponse cancelAttempt(
      @PathVariable String orderId,
      @PathVariable String attemptId,
      @RequestHeader("X-Merchant-Id") String merchantId) {
    owned(orderId, merchantId);
    paymentAttemptService.get(attemptId, orderId);
    return attemptResponse(paymentAttemptService.cancel(attemptId));
  }

  @PostMapping("/{orderId}/attempts/{attemptId}/retry")
  public OrderDtos.MerchantAttemptResponse retryAttempt(
      @PathVariable String orderId,
      @PathVariable String attemptId,
      @RequestHeader("X-Merchant-Id") String merchantId) {
    var order = owned(orderId, merchantId);
    return attemptResponse(paymentAttemptService.retry(attemptId, order));
  }

  private static OrderDtos.MerchantAttemptResponse attemptResponse(
      com.example.payments.trade.service.domain.PaymentAttempt attempt) {
    return OrderDtos.MerchantAttemptResponse.from(attempt);
  }

  private com.example.payments.trade.service.domain.PaymentOrder owned(
      String orderId, String merchantId) {
    var order = orderService.get(orderId);
    if (!merchantId.equals(order.merchantId()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
    return order;
  }

  @PostMapping("/attempts/callback")
  public Map<String, Object> attemptCallback(@Valid @RequestBody CallbackRequest request) {
    var attempt =
        paymentAttemptService.callback(
            request.channelId(), request.rawPayload(), request.signature(), request.callbackId());
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
      @NotBlank String channelId,
      @NotBlank String callbackId,
      @NotBlank String rawPayload,
      @NotBlank String signature) {}
}
