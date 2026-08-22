package com.example.payments.trade.service.service;

import com.example.payments.trade.service.config.OutboxProperties;
import com.example.payments.trade.service.mapper.PaymentOutboxEventRepository;
import com.example.payments.trade.service.model.*;
import java.time.Instant;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutboxPublisher {
  private final PaymentOutboxEventRepository repository;
  private final RocketMQTemplate rocketMQTemplate;
  private final OutboxProperties properties;

  public PaymentOutboxPublisher(
      PaymentOutboxEventRepository repository,
      RocketMQTemplate rocketMQTemplate,
      OutboxProperties properties) {
    this.repository = repository;
    this.rocketMQTemplate = rocketMQTemplate;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${trade.outbox.publish-ms:5000}")
  public void publish() {
    Instant now = Instant.now();
    for (PaymentOutboxEventEntity event :
        repository.claimPending(now, properties.batchSize(), properties.claimTimeoutSeconds())) {
      try {
        SendResult result = rocketMQTemplate.syncSend(event.getEventType(), event.getPayload());
        if (result != null) repository.markPublished(event.getEventId(), event.getClaimToken());
      } catch (RuntimeException exception) {
        long delay =
            Math.min(
                properties.retryMaxSeconds(),
                properties.retryBaseSeconds()
                    * (1L
                        << Math.min(
                            event.getAttemptCount() == null ? 0 : event.getAttemptCount(), 30)));
        repository.markFailed(
            event.getEventId(),
            event.getClaimToken(),
            now.plusSeconds(delay),
            exception.getMessage(),
            exception.getClass().getSimpleName(),
            properties.maxAttempts());
      }
    }
  }
}
