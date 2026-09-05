package com.example.payments.trade.service.service;

import com.example.payments.trade.service.config.OrderExpirationProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderExpirationWorker {
  private final OrderService orderService;
  private final OrderExpirationProperties properties;

  @Scheduled(fixedDelayString = "${trade.order-expiration.sweep-ms:5000}")
  public void expireDueOrders() {
    orderService.expireDue(Instant.now(), properties.batchSize());
  }
}
