package com.example.payments.fund.service.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("merchant_settlement_detail")
public class MerchantSettlementDetailEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  private String detailId;
  private String merchantId;
  private String accountId;
  private String orderId;
  private BigDecimal orderAmount;
  private BigDecimal feeAmount;
  private BigDecimal settlementAmount;
  private BigDecimal refundedAmount;
  private String currency;
  private String settlementCycle;
  private Boolean autoSettlement;
  private BigDecimal minSettlementAmount;
  private LocalDate expectedSettlementDate;
  private LocalDate actualSettlementDate;
  private String status;
  private String settlementBatchId;
  private Long fundAccountEntryId;
  private String remark;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getDetailId() {
    return detailId;
  }

  public void setDetailId(String detailId) {
    this.detailId = detailId;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String merchantId) {
    this.merchantId = merchantId;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String accountId) {
    this.accountId = accountId;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public BigDecimal getOrderAmount() {
    return orderAmount;
  }

  public void setOrderAmount(BigDecimal orderAmount) {
    this.orderAmount = orderAmount;
  }

  public BigDecimal getFeeAmount() {
    return feeAmount;
  }

  public void setFeeAmount(BigDecimal feeAmount) {
    this.feeAmount = feeAmount;
  }

  public BigDecimal getSettlementAmount() {
    return settlementAmount;
  }

  public void setSettlementAmount(BigDecimal settlementAmount) {
    this.settlementAmount = settlementAmount;
  }

  public BigDecimal getRefundedAmount() {
    return refundedAmount;
  }

  public void setRefundedAmount(BigDecimal refundedAmount) {
    this.refundedAmount = refundedAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getSettlementCycle() {
    return settlementCycle;
  }

  public void setSettlementCycle(String settlementCycle) {
    this.settlementCycle = settlementCycle;
  }

  public Boolean getAutoSettlement() {
    return autoSettlement;
  }

  public void setAutoSettlement(Boolean autoSettlement) {
    this.autoSettlement = autoSettlement;
  }

  public BigDecimal getMinSettlementAmount() {
    return minSettlementAmount;
  }

  public void setMinSettlementAmount(BigDecimal minSettlementAmount) {
    this.minSettlementAmount = minSettlementAmount;
  }

  public LocalDate getExpectedSettlementDate() {
    return expectedSettlementDate;
  }

  public void setExpectedSettlementDate(LocalDate expectedSettlementDate) {
    this.expectedSettlementDate = expectedSettlementDate;
  }

  public LocalDate getActualSettlementDate() {
    return actualSettlementDate;
  }

  public void setActualSettlementDate(LocalDate actualSettlementDate) {
    this.actualSettlementDate = actualSettlementDate;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getSettlementBatchId() {
    return settlementBatchId;
  }

  public void setSettlementBatchId(String settlementBatchId) {
    this.settlementBatchId = settlementBatchId;
  }

  public Long getFundAccountEntryId() {
    return fundAccountEntryId;
  }

  public void setFundAccountEntryId(Long fundAccountEntryId) {
    this.fundAccountEntryId = fundAccountEntryId;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
