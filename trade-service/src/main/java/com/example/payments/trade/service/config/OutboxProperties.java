package com.example.payments.trade.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trade.outbox")
public record OutboxProperties(int batchSize, int maxAttempts, long retryBaseSeconds,
    long retryMaxSeconds, long claimTimeoutSeconds) {
  public OutboxProperties {
    if (batchSize <= 0 || maxAttempts <= 0 || retryBaseSeconds <= 0 || retryMaxSeconds < retryBaseSeconds
        || claimTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("invalid outbox configuration");
    }
  }
}
