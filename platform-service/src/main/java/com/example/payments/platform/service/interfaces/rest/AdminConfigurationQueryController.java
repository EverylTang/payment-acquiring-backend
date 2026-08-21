package com.example.payments.platform.service.interfaces.rest;

import com.example.payments.platform.service.application.ConfigurationSnapshotService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/configurations")
public class AdminConfigurationQueryController {
  private final ConfigurationSnapshotService snapshotService;
  private final ConfigurationController configurationController;

  public AdminConfigurationQueryController(ConfigurationSnapshotService snapshotService, ConfigurationController configurationController) {
    this.snapshotService = snapshotService;
    this.configurationController = configurationController;
  }

  @GetMapping("/snapshot")
  public Map<String, Object> snapshot(
      @RequestParam String merchantId,
      @RequestParam String productCode,
      @RequestParam String paymentMethod,
      @RequestParam(defaultValue = "US") String country,
      @RequestParam String currency,
      @RequestParam(defaultValue = "1.00") BigDecimal amount) {
    return snapshotService.snapshot(merchantId, productCode, paymentMethod, country, currency, amount);
  }

  @GetMapping("/channels/{channelId}/health")
  public Map<String, Object> channelHealth(@PathVariable String channelId) {
    return configurationController.channelHealth(channelId);
  }
}
