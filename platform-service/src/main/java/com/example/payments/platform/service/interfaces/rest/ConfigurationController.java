package com.example.payments.platform.service.interfaces.rest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/configurations")
public class ConfigurationController {
  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(
      @RequestParam String merchantId,
      @RequestParam String productCode,
      @RequestParam String paymentMethod,
      @RequestParam String currency) {
    return Map.of(
        "merchantId", merchantId,
        "productCode", productCode,
        "paymentMethod", paymentMethod,
        "currency", currency,
        "configVersion", 1,
        "product", Map.of("enabled", true, "supportsRefund", true),
        "route", Map.of("channelId", "simulated-channel", "priority", 1, "weight", 100),
        "pricing", Map.of("feeRate", new BigDecimal("0.0200"), "mode", "INCLUSIVE", "scale", 2),
        "risk", Map.of("decision", "PASS"),
        "candidates", List.of("simulated-channel"));
  }

  @GetMapping("/channels/{channelId}/health")
  public Map<String, Object> channelHealth(@PathVariable String channelId) {
    return Map.of("channelId", channelId, "status", "UP", "checkedAt", System.currentTimeMillis());
  }
}
