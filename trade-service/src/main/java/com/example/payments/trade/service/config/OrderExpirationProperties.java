package com.example.payments.trade.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trade.order-expiration")
public record OrderExpirationProperties(
    int batchSize, long minValiditySeconds, long maxValiditySeconds) {
  public OrderExpirationProperties {
    if (batchSize <= 0
        || minValiditySeconds <= 0
        || maxValiditySeconds < minValiditySeconds) {
      throw new IllegalArgumentException("invalid order expiration configuration");
    }
  }

  public static OrderExpirationProperties defaults() {
    return new OrderExpirationProperties(100, 60, 86400);
  }
}
