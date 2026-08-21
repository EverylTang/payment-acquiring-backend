package com.example.payments.trade.service.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.trade.service.infrastructure.persistence.PaymentOutboxEventEntity;
import com.example.payments.trade.service.infrastructure.persistence.PaymentOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class PaymentOutboxPublisherTest {
  private final PaymentOutboxEventRepository repository = org.mockito.Mockito.mock(PaymentOutboxEventRepository.class);
  private final RocketMQTemplate template = org.mockito.Mockito.mock(RocketMQTemplate.class);
  private final PaymentOutboxPublisher publisher = new PaymentOutboxPublisher(repository, template);

  @Test
  void publishedMessageIsMarked() {
    var event = event();
    when(repository.findPending(ArgumentMatchers.any(Instant.class), ArgumentMatchers.eq(50))).thenReturn(List.of(event));
    when(template.syncSend("PAYMENT_SUCCEEDED", event.getPayload())).thenReturn(new SendResult());

    publisher.publish();

    verify(repository).markPublished("event-1");
  }

  @Test
  void failedMessageIsScheduledForRetry() {
    var event = event();
    when(repository.findPending(ArgumentMatchers.any(Instant.class), ArgumentMatchers.eq(50))).thenReturn(List.of(event));
    when(template.syncSend("PAYMENT_SUCCEEDED", event.getPayload())).thenThrow(new IllegalStateException("broker unavailable"));

    publisher.publish();

    verify(repository).markFailed(ArgumentMatchers.eq("event-1"), ArgumentMatchers.any(Instant.class), ArgumentMatchers.eq("broker unavailable"));
  }

  private static PaymentOutboxEventEntity event() {
    var event = new PaymentOutboxEventEntity();
    event.setEventId("event-1");
    event.setPayload("{}");
    return event;
  }
}
