package com.example.payments.trade.service.infrastructure.persistence;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOutboxEventRepository {
  private final PaymentOutboxEventMapper mapper;
  private final String workerId = UUID.randomUUID().toString();

  public PaymentOutboxEventRepository(PaymentOutboxEventMapper mapper) { this.mapper = mapper; }

  public boolean insert(String eventId, String aggregateId, String eventType, String payload) {
    var now = toLocal(Instant.now());
    var event = new PaymentOutboxEventEntity();
    event.setEventId(eventId); event.setAggregateType("PAYMENT_ORDER"); event.setAggregateId(aggregateId);
    event.setEventType(eventType); event.setPayload(payload); event.setStatus("PENDING"); event.setAttemptCount(0);
    event.setNextRetryAt(now); event.setCreatedAt(now);
    try { mapper.insert(event); return true; } catch (DuplicateKeyException duplicate) { return false; }
  }

  public List<PaymentOutboxEventEntity> claimPending(Instant now, int limit, long lockSeconds) {
    mapper.recoverExpiredClaims(toLocal(now));
    return mapper.findPending(toLocal(now), limit).stream()
        .filter(event -> {
          String claimToken = UUID.randomUUID().toString();
          event.setClaimToken(claimToken);
          return mapper.claim(event.getEventId(), workerId, claimToken, toLocal(now),
              toLocal(now.plusSeconds(lockSeconds))) == 1;
        })
        .peek(event -> { event.setStatus("PROCESSING"); event.setLockedBy(workerId); })
        .toList();
  }

  public boolean markPublished(String eventId, String claimToken) {
    Instant now = Instant.now();
    return mapper.markPublished(eventId, claimToken, toLocal(now), toLocal(now)) == 1;
  }

  public boolean markFailed(String eventId, String claimToken, Instant nextRetryAt, String error,
      String failureType, int maxAttempts) {
    Instant now = Instant.now();
    return mapper.markFailed(eventId, claimToken, toLocal(now), toLocal(nextRetryAt), truncate(error), failureType,
        toLocal(now), maxAttempts) == 1;
  }

  public PaymentOutboxEventEntity findByEventId(String eventId) { return mapper.findByEventId(eventId); }

  public List<PaymentOutboxEventEntity> findDead(int limit) { return mapper.findDead(limit); }

  public boolean redrive(String eventId, Instant now) {
    return mapper.redrive(eventId, toLocal(now)) == 1;
  }

  public int insertAudit(String eventId, String operator, String reason, String fromStatus,
      String toStatus, String requestId, Instant now) {
    return mapper.insertAudit(eventId, operator, reason, fromStatus, toStatus, requestId, toLocal(now));
  }

  public String workerId() { return workerId; }

  private static String truncate(String value) {
    if (value == null) return null;
    return value.length() <= 512 ? value : value.substring(0, 512);
  }

  private static LocalDateTime toLocal(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
