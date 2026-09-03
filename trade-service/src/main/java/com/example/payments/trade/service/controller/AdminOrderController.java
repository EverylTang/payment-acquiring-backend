package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.OrderService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/orders")
@RequiredArgsConstructor
public class AdminOrderController {
  private final OrderService orderService;
  private final AdminRequestAuthorizer authorizer;

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(required = false) String merchantId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String currency,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:list");
    return orderService.list(merchantId, status, currency, page, pageSize);
  }

  @GetMapping("/statistics")
  public Map<String, Object> statistics(
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "order:statistics");
    return orderService.statistics();
  }
}
