package com.example.payments.trade.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trade.attempt.query")
public record AttemptQueryProperties(int batchSize, int maxCount, long lockSeconds, long retryBaseSeconds,
    long retryMaxSeconds) {
  public AttemptQueryProperties {
    if (batchSize <= 0) batchSize = 50;
    if (maxCount <= 0) maxCount = 8;
    if (lockSeconds <= 0) lockSeconds = 60;
    if (retryBaseSeconds <= 0) retryBaseSeconds = 30;
    if (retryMaxSeconds <= 0) retryMaxSeconds = 300;
  }
}
