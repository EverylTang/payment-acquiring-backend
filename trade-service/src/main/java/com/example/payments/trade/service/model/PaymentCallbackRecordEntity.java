package com.example.payments.trade.service.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("payment_callback_record")
public class PaymentCallbackRecordEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String callbackId;
  private String attemptId;
  private String channelOrderId;
  private String rawPayload;
  private String signature;
  private String status;
  private LocalDateTime receivedAt;
  private LocalDateTime processedAt;

  public Long getId() { return id; }
  public void setId(Long value) { id = value; }
  public String getCallbackId() { return callbackId; }
  public void setCallbackId(String value) { callbackId = value; }
  public String getAttemptId() { return attemptId; }
  public void setAttemptId(String value) { attemptId = value; }
  public String getChannelOrderId() { return channelOrderId; }
  public void setChannelOrderId(String value) { channelOrderId = value; }
  public String getRawPayload() { return rawPayload; }
  public void setRawPayload(String value) { rawPayload = value; }
  public String getSignature() { return signature; }
  public void setSignature(String value) { signature = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public LocalDateTime getReceivedAt() { return receivedAt; }
  public void setReceivedAt(LocalDateTime value) { receivedAt = value; }
  public LocalDateTime getProcessedAt() { return processedAt; }
  public void setProcessedAt(LocalDateTime value) { processedAt = value; }
}
