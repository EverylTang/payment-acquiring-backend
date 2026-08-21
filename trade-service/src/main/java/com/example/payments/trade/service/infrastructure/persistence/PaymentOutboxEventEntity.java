package com.example.payments.trade.service.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("payment_outbox_event")
public class PaymentOutboxEventEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String eventId;
  private String aggregateType;
  private String aggregateId;
  private String eventType;
  private String payload;
  private String status;
  private Integer attemptCount;
  private LocalDateTime nextRetryAt;
  private String lastError;
  private LocalDateTime createdAt;
  private LocalDateTime publishedAt;
  private String lockedBy;
  private LocalDateTime lockedAt;
  private LocalDateTime lockUntil;
  private String claimToken;
  private String lastFailureType;
  private LocalDateTime firstFailedAt;
  private LocalDateTime deadAt;

  public Long getId() { return id; }
  public void setId(Long value) { id = value; }
  public String getEventId() { return eventId; }
  public void setEventId(String value) { eventId = value; }
  public String getAggregateType() { return aggregateType; }
  public void setAggregateType(String value) { aggregateType = value; }
  public String getAggregateId() { return aggregateId; }
  public void setAggregateId(String value) { aggregateId = value; }
  public String getEventType() { return eventType; }
  public void setEventType(String value) { eventType = value; }
  public String getPayload() { return payload; }
  public void setPayload(String value) { payload = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public Integer getAttemptCount() { return attemptCount; }
  public void setAttemptCount(Integer value) { attemptCount = value; }
  public LocalDateTime getNextRetryAt() { return nextRetryAt; }
  public void setNextRetryAt(LocalDateTime value) { nextRetryAt = value; }
  public String getLastError() { return lastError; }
  public void setLastError(String value) { lastError = value; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime value) { createdAt = value; }
  public LocalDateTime getPublishedAt() { return publishedAt; }
  public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
  public String getLockedBy() { return lockedBy; }
  public void setLockedBy(String value) { lockedBy = value; }
  public LocalDateTime getLockedAt() { return lockedAt; }
  public void setLockedAt(LocalDateTime value) { lockedAt = value; }
  public LocalDateTime getLockUntil() { return lockUntil; }
  public void setLockUntil(LocalDateTime value) { lockUntil = value; }
  public String getClaimToken() { return claimToken; }
  public void setClaimToken(String value) { claimToken = value; }
  public String getLastFailureType() { return lastFailureType; }
  public void setLastFailureType(String value) { lastFailureType = value; }
  public LocalDateTime getFirstFailedAt() { return firstFailedAt; }
  public void setFirstFailedAt(LocalDateTime value) { firstFailedAt = value; }
  public LocalDateTime getDeadAt() { return deadAt; }
  public void setDeadAt(LocalDateTime value) { deadAt = value; }
}
