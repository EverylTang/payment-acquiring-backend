package com.example.payments.trade.service.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("refund_attempt")
public class RefundAttemptEntity {
  private Long id;
  private String attemptId;
  private String refundId;
  private String channelId;
  private String channelRequestNo;
  private Integer attemptNo;
  private String status;
  private String requestSnapshot;
  private String responseSnapshot;
  private String failureCode;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long v) {
    id = v;
  }

  public String getAttemptId() {
    return attemptId;
  }

  public void setAttemptId(String v) {
    attemptId = v;
  }

  public String getRefundId() {
    return refundId;
  }

  public void setRefundId(String v) {
    refundId = v;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String v) {
    channelId = v;
  }

  public String getChannelRequestNo() {
    return channelRequestNo;
  }

  public void setChannelRequestNo(String v) {
    channelRequestNo = v;
  }

  public Integer getAttemptNo() {
    return attemptNo;
  }

  public void setAttemptNo(Integer v) {
    attemptNo = v;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String v) {
    status = v;
  }

  public String getRequestSnapshot() {
    return requestSnapshot;
  }

  public void setRequestSnapshot(String v) {
    requestSnapshot = v;
  }

  public String getResponseSnapshot() {
    return responseSnapshot;
  }

  public void setResponseSnapshot(String v) {
    responseSnapshot = v;
  }

  public String getFailureCode() {
    return failureCode;
  }

  public void setFailureCode(String v) {
    failureCode = v;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(LocalDateTime v) {
    startedAt = v;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(LocalDateTime v) {
    completedAt = v;
  }
}
