package com.example.payments.fund.service.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("refund_event_consumption")
public class RefundEventConsumptionEntity {
  private Long id;
  private String eventId;
  private String refundId;
  private String payloadHash;
  private String status;
  private String lastError;
  private Integer consumeCount;
  private LocalDateTime createdAt;
  private LocalDateTime processedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String v) {
    eventId = v;
  }

  public String getRefundId() {
    return refundId;
  }

  public void setRefundId(String v) {
    refundId = v;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public void setPayloadHash(String v) {
    payloadHash = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String v) {
    lastError = v;
  }

  public Integer getConsumeCount() {
    return consumeCount;
  }

  public void setConsumeCount(Integer v) {
    consumeCount = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }

  public LocalDateTime getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(LocalDateTime v) {
    processedAt = v;
  }
}
