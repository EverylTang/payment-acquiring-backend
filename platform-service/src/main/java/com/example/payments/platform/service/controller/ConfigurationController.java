package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.ConfigurationSnapshotService;
import com.example.payments.platform.service.service.PlatformDataService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/configurations")
public class ConfigurationController {
  private final ConfigurationSnapshotService snapshotService;
  private final PlatformDataService mybatisClient;

  public ConfigurationController( ConfigurationSnapshotService snapshotService, PlatformDataService mybatisClient) {
    this.snapshotService = snapshotService;
    this.mybatisClient = mybatisClient;
  }

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
    var status =
        mybatisClient
            .sql("SELECT status FROM channel WHERE channel_id = :id")
            .param("id", channelId)
            .query(String.class)
            .optional()
            .orElse("NOT_FOUND");
    return Map.of(
        "channelId",
        channelId,
        "status",
        status.equals("ACTIVE") ? "UP" : "DOWN",
        "checkedAt",
        System.currentTimeMillis());
  }
}
