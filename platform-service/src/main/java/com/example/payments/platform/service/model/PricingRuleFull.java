package com.example.payments.platform.service.model;

import java.math.BigDecimal;

public class PricingRuleFull {
  private Long id;
  private String ruleId;
  private Long releaseVersion;
  private String productCode;
  private String merchantId;
  private String channelId;
  private String currency;
  private BigDecimal feeRate;
  private BigDecimal fixedFee;
  private BigDecimal extraFee;
  private BigDecimal minFee;
  private BigDecimal maxFee;
  private String feeType;
  private String tieredFees;
  private String feeMode;
  private BigDecimal minAmount;
  private BigDecimal maxAmount;
  private String status;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getRuleId() {
    return ruleId;
  }

  public void setRuleId(String ruleId) {
    this.ruleId = ruleId;
  }

  public Long getReleaseVersion() {
    return releaseVersion;
  }

  public void setReleaseVersion(Long releaseVersion) {
    this.releaseVersion = releaseVersion;
  }

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String merchantId) {
    this.merchantId = merchantId;
  }

  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(String channelId) {
    this.channelId = channelId;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getFeeRate() {
    return feeRate;
  }

  public void setFeeRate(BigDecimal feeRate) {
    this.feeRate = feeRate;
  }

  public BigDecimal getFixedFee() {
    return fixedFee;
  }

  public void setFixedFee(BigDecimal fixedFee) {
    this.fixedFee = fixedFee;
  }

  public BigDecimal getExtraFee() {
    return extraFee;
  }

  public void setExtraFee(BigDecimal extraFee) {
    this.extraFee = extraFee;
  }

  public BigDecimal getMinFee() {
    return minFee;
  }

  public void setMinFee(BigDecimal minFee) {
    this.minFee = minFee;
  }

  public BigDecimal getMaxFee() {
    return maxFee;
  }

  public void setMaxFee(BigDecimal maxFee) {
    this.maxFee = maxFee;
  }

  public String getFeeType() {
    return feeType;
  }

  public void setFeeType(String feeType) {
    this.feeType = feeType;
  }

  public String getTieredFees() {
    return tieredFees;
  }

  public void setTieredFees(String tieredFees) {
    this.tieredFees = tieredFees;
  }

  public String getFeeMode() {
    return feeMode;
  }

  public void setFeeMode(String feeMode) {
    this.feeMode = feeMode;
  }

  public BigDecimal getMinAmount() {
    return minAmount;
  }

  public void setMinAmount(BigDecimal minAmount) {
    this.minAmount = minAmount;
  }

  public BigDecimal getMaxAmount() {
    return maxAmount;
  }

  public void setMaxAmount(BigDecimal maxAmount) {
    this.maxAmount = maxAmount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
