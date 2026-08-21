package com.example.payments.trade.service.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOutboxEventRepository {
  private final PaymentOutboxEventMapper mapper;

  public PaymentOutboxEventRepository(PaymentOutboxEventMapper mapper) { this.mapper = mapper; }

  public boolean insert(String eventId, String aggregateId, String eventType, String payload) {
    var event = new PaymentOutboxEventEntity();
    event.setEventId(eventId);
    event.setAggregateType("PAYMENT_ORDER");
    event.setAggregateId(aggregateId);
    event.setEventType(eventType);
    event.setPayload(payload);
    event.setStatus("PENDING");
    event.setAttemptCount(0);
    event.setNextRetryAt(toLocal(Instant.now()));
    event.setCreatedAt(toLocal(Instant.now()));
    try {
      mapper.insert(event);
      return true;
    } catch (DuplicateKeyException duplicate) {
      return false;
    }
  }

  public List<PaymentOutboxEventEntity> findPending(Instant now, int limit) {
    return mapper.findPending(toLocal(now), limit);
  }

  public boolean markPublished(String eventId) {
    return mapper.markPublished(eventId, toLocal(Instant.now())) == 1;
  }

  public boolean markFailed(String eventId, Instant nextRetryAt, String error) {
    return mapper.markFailed(eventId, toLocal(nextRetryAt), error) == 1;
  }

  private static LocalDateTime toLocal(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
