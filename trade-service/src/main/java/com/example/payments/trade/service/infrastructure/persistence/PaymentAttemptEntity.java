package com.example.payments.trade.service.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("payment_attempt")
public class PaymentAttemptEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;
  private String attemptId;
  private String orderId;
  private String channelId;
  private String channelRequestNo;
  private Integer attemptNo;
  private String status;
  private String requestSummary;
  private String responseSummary;
  private String failureCode;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;
  private Long version;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getAttemptId() { return attemptId; }
  public void setAttemptId(String value) { attemptId = value; }
  public String getOrderId() { return orderId; }
  public void setOrderId(String value) { orderId = value; }
  public String getChannelId() { return channelId; }
  public void setChannelId(String value) { channelId = value; }
  public String getChannelRequestNo() { return channelRequestNo; }
  public void setChannelRequestNo(String value) { channelRequestNo = value; }
  public Integer getAttemptNo() { return attemptNo; }
  public void setAttemptNo(Integer value) { attemptNo = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public String getRequestSummary() { return requestSummary; }
  public void setRequestSummary(String value) { requestSummary = value; }
  public String getResponseSummary() { return responseSummary; }
  public void setResponseSummary(String value) { responseSummary = value; }
  public String getFailureCode() { return failureCode; }
  public void setFailureCode(String value) { failureCode = value; }
  public LocalDateTime getStartedAt() { return startedAt; }
  public void setStartedAt(LocalDateTime value) { startedAt = value; }
  public LocalDateTime getCompletedAt() { return completedAt; }
  public void setCompletedAt(LocalDateTime value) { completedAt = value; }
  public Long getVersion() { return version; }
  public void setVersion(Long value) { version = value; }
}
