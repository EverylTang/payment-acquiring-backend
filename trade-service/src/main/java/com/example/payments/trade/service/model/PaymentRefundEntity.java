package com.example.payments.trade.service.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("payment_refund")
public class PaymentRefundEntity {
  private Long id;
  private String refundId;
  private String orderId;
  private String merchantId;
  private String idempotencyKey;
  private BigDecimal amount;
  private String currency;
  private String channelRefundId;
  private String status;
  private Integer attemptCount;
  private LocalDateTime nextAttemptAt;
  private String lastError;
  private String callbackId;
  private String processingOwner;
  private LocalDateTime processingUntil;
  private String reason;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime completedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long value) {
    id = value;
  }

  public String getRefundId() {
    return refundId;
  }

  public void setRefundId(String value) {
    refundId = value;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String value) {
    orderId = value;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String value) {
    merchantId = value;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String value) {
    idempotencyKey = value;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal value) {
    amount = value;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String value) {
    currency = value;
  }

  public String getChannelRefundId() {
    return channelRefundId;
  }

  public void setChannelRefundId(String value) {
    channelRefundId = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    status = value;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(Integer value) {
    attemptCount = value;
  }

  public LocalDateTime getNextAttemptAt() {
    return nextAttemptAt;
  }

  public void setNextAttemptAt(LocalDateTime value) {
    nextAttemptAt = value;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String value) {
    lastError = value;
  }

  public String getCallbackId() {
    return callbackId;
  }

  public void setCallbackId(String value) {
    callbackId = value;
  }

  public String getProcessingOwner() {
    return processingOwner;
  }

  public void setProcessingOwner(String value) {
    processingOwner = value;
  }

  public LocalDateTime getProcessingUntil() {
    return processingUntil;
  }

  public void setProcessingUntil(LocalDateTime value) {
    processingUntil = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime value) {
    completedAt = value;
  }
}
