package com.example.payments.trade.service.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("refund_callback_record")
public class RefundCallbackRecordEntity {
  private Long id;
  private String callbackId;
  private String refundId;
  private String payloadHash;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime processedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getCallbackId() {
    return callbackId;
  }

  public void setCallbackId(String v) {
    callbackId = v;
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
