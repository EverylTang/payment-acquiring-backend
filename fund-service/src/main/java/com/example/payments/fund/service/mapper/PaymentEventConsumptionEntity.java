package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("payment_event_consumption")
public class PaymentEventConsumptionEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String eventId;
  private String eventType;
  private String orderId;
  private String attemptId;
  private String merchantId;
  private BigDecimal amount;
  private String currency;
  private String payload;
  private String payloadHash;
  private String status;
  private Integer consumeCount;
  private LocalDateTime firstReceivedAt;
  private LocalDateTime lastReceivedAt;
  private LocalDateTime processedAt;
  private String lastError;
  private String failureType;
  private String ledgerEntryId;
  private String processingOwner;
  private LocalDateTime processingUntil;

  public Long getId() { return id; }
  public void setId(Long value) { id = value; }
  public String getEventId() { return eventId; }
  public void setEventId(String value) { eventId = value; }
  public String getEventType() { return eventType; }
  public void setEventType(String value) { eventType = value; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String value) { orderId = value; }
  public String getAttemptId() { return attemptId; }
  public void setAttemptId(String value) { attemptId = value; }
  public String getMerchantId() { return merchantId; }
  public void setMerchantId(String value) { merchantId = value; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal value) { amount = value; }
  public String getCurrency() { return currency; }
  public void setCurrency(String value) { currency = value; }
  public String getPayload() { return payload; }
  public void setPayload(String value) { payload = value; }
  public String getPayloadHash() { return payloadHash; }
  public void setPayloadHash(String value) { payloadHash = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public Integer getConsumeCount() { return consumeCount; }
  public void setConsumeCount(Integer value) { consumeCount = value; }
  public LocalDateTime getFirstReceivedAt() { return firstReceivedAt; }
  public void setFirstReceivedAt(LocalDateTime value) { firstReceivedAt = value; }
  public LocalDateTime getLastReceivedAt() { return lastReceivedAt; }
  public void setLastReceivedAt(LocalDateTime value) { lastReceivedAt = value; }
  public LocalDateTime getProcessedAt() { return processedAt; }
  public void setProcessedAt(LocalDateTime value) { processedAt = value; }
  public String getLastError() { return lastError; }
  public void setLastError(String value) { lastError = value; }
  public String getFailureType() { return failureType; }
  public void setFailureType(String value) { failureType = value; }
  public String getLedgerEntryId() { return ledgerEntryId; }
  public void setLedgerEntryId(String value) { ledgerEntryId = value; }
  public String getProcessingOwner() { return processingOwner; }
  public void setProcessingOwner(String value) { processingOwner = value; }
  public LocalDateTime getProcessingUntil() { return processingUntil; }
  public void setProcessingUntil(LocalDateTime value) { processingUntil = value; }
}
