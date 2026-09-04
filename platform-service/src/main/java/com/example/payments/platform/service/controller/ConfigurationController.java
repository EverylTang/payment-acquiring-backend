package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.ConfigurationHealthService;
import com.example.payments.platform.service.service.ConfigurationSnapshotService;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/configurations")
@RequiredArgsConstructor
public class ConfigurationController {
  private final ConfigurationSnapshotService snapshotService;
  private final ConfigurationHealthService healthService;
  private final com.example.payments.platform.service.service.RiskAdminService riskAdminService;

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(
      @RequestParam String merchantId,
      @RequestParam String productCode,
      @RequestParam String paymentMethod,
      @RequestParam(defaultValue = "US") String country,
      @RequestParam String currency,
      @RequestParam(defaultValue = "1.00") BigDecimal amount) {
    return snapshotService.snapshot(
        merchantId, productCode, paymentMethod, country, currency, amount);
  }

  @GetMapping("/channels/{channelId}/health")
  public Map<String, Object> channelHealth(@PathVariable String channelId) {
    return healthService.health(channelId);
  }

  @GetMapping("/products/{productCode}/type")
  public Map<String, String> productType(@PathVariable String productCode) {
    return Map.of("productCode", productCode, "productType", snapshotService.productType(productCode));
  }

  @GetMapping("/channels/{channelId}/runtime")
  public Map<String, Object> channelRuntime(@PathVariable String channelId) {
    return snapshotService.channelRuntime(channelId);
  }

  @PostMapping("/risk-events")
  public void riskEvent(
      @jakarta.validation.Valid @RequestBody
          com.example.payments.platform.service.service.RiskAdminService.RiskDecisionRequest request) {
    riskAdminService.recordDecision(request);
  }
}
