package com.example.payments.fund.service.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("merchant_settlement_rule")
public class MerchantSettlementRuleEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  private String merchantId;
  private String currency;
  private String settlementCycle;
  private Integer cycleDays;
  private BigDecimal minSettlementAmount;
  private BigDecimal feeRate;
  private Boolean autoSettlement;
  private String status;
  private LocalDate effectiveDate;
  private LocalDate expireDate;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String merchantId) {
    this.merchantId = merchantId;
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

  public Integer getCycleDays() {
    return cycleDays;
  }

  public void setCycleDays(Integer cycleDays) {
    this.cycleDays = cycleDays;
  }

  public BigDecimal getMinSettlementAmount() {
    return minSettlementAmount;
  }

  public void setMinSettlementAmount(BigDecimal minSettlementAmount) {
    this.minSettlementAmount = minSettlementAmount;
  }

  public BigDecimal getFeeRate() {
    return feeRate;
  }

  public void setFeeRate(BigDecimal feeRate) {
    this.feeRate = feeRate;
  }

  public Boolean getAutoSettlement() {
    return autoSettlement;
  }

  public void setAutoSettlement(Boolean autoSettlement) {
    this.autoSettlement = autoSettlement;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public void setEffectiveDate(LocalDate effectiveDate) {
    this.effectiveDate = effectiveDate;
  }

  public LocalDate getExpireDate() {
    return expireDate;
  }

  public void setExpireDate(LocalDate expireDate) {
    this.expireDate = expireDate;
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
