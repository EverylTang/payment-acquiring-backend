package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/orders")
public class AdminOrderController {
  private final PaymentOrderRepository repository;

  public AdminOrderController(PaymentOrderRepository repository) { this.repository = repository; }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String merchantId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String currency,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("invalid pagination");
    var items = repository.search(merchantId, status, currency, page, pageSize).stream().map(OrderDtos.OrderResponse::from).toList();
    return Map.of("items", items, "page", page, "pageSize", pageSize, "total", repository.count(merchantId, status, currency));
  }

  @GetMapping("/statistics")
  public Map<String, Object> statistics() {
    var statistics = repository.statistics();
    BigDecimal successRate = statistics.total() == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(statistics.successful() * 100.0 / statistics.total()).setScale(2, java.math.RoundingMode.HALF_UP);
    return Map.of("totalOrders", statistics.total(), "successfulOrders", statistics.successful(), "paymentSuccessRate", successRate, "paymentVolume", statistics.volume(), "activeMerchants", statistics.merchants());
  }
}
