package com.example.payments.trade.service.application;

import com.example.payments.trade.service.infrastructure.persistence.PaymentOutboxEventEntity;
import com.example.payments.trade.service.infrastructure.persistence.PaymentOutboxEventRepository;
import java.time.Instant;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutboxPublisher {
  private final PaymentOutboxEventRepository repository;
  private final RocketMQTemplate rocketMQTemplate;

  public PaymentOutboxPublisher(PaymentOutboxEventRepository repository, RocketMQTemplate rocketMQTemplate) {
    this.repository = repository;
    this.rocketMQTemplate = rocketMQTemplate;
  }

  @Scheduled(fixedDelayString = "${trade.outbox.publish-ms:5000}")
  public void publish() {
    for (PaymentOutboxEventEntity event : repository.findPending(Instant.now(), 50)) {
      try {
        SendResult result = rocketMQTemplate.syncSend("PAYMENT_SUCCEEDED", event.getPayload());
        if (result != null) repository.markPublished(event.getEventId());
      } catch (RuntimeException exception) {
        repository.markFailed(event.getEventId(), Instant.now().plusSeconds(30), exception.getMessage());
      }
    }
  }
}
