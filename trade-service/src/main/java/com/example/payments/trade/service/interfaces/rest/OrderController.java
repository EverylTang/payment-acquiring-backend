package com.example.payments.trade.service.interfaces.rest;

import com.example.payments.trade.service.application.OrderService;
import com.example.payments.trade.service.domain.OrderStatus;
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

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
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
    var order = orderService.create(new OrderService.CreateOrderCommand(request.merchantId(),
        request.merchantOrderNo(), request.productCode(), request.paymentMethod(), request.country(),
        request.currency(), request.amount(), idempotencyKey, request.expireAt()));
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

  @PostMapping("/{orderId}/callback")
  public OrderDtos.OrderResponse callback(@PathVariable(name = "orderId") String orderId, @RequestParam(name = "status") @NotBlank String status) {
    try {
      return OrderDtos.OrderResponse.from(orderService.callback(orderId, OrderStatus.valueOf(status.toUpperCase())));
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported status", exception);
    }
  }
}
