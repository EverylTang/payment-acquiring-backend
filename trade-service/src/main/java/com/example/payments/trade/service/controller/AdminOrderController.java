package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.OrderService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/orders")
public class AdminOrderController {
  private final OrderService orderService;

  public AdminOrderController(OrderService orderService) { this.orderService = orderService; }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String merchantId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String currency,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return orderService.list(merchantId, status, currency, page, pageSize);
  }

  @GetMapping("/statistics")
  public Map<String, Object> statistics() {
    return orderService.statistics();
  }
}
