package com.example.payments.trade.service.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("payment_order")
public class PaymentOrderEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  private String orderId;
  private String merchantId;
  private String merchantOrderNo;
  private String productCode;
  private String orderType;
  private String paymentMethod;
  private String country;
  private String currency;
  private BigDecimal amount;
  private BigDecimal feeAmount;
  private BigDecimal payerPayableAmount;
  private BigDecimal netAmount;
  private String feeBearer;
  private String status;
  private String idempotencyKey;

  @TableField("merchant_request_snapshot")
  private String merchantRequestSnapshot;

  @TableField("route_snapshot_json")
  private String routeSnapshotJson;

  @TableField("pricing_snapshot_json")
  private String pricingSnapshotJson;

  private LocalDateTime expireAt;
  private LocalDateTime createdAt;
  private LocalDateTime paidAt;
  private String paymentToken;
  private String notifyUrl;
  private String returnUrl;
  private String customerReference;
  private String payoutDestinationRef;
  private String description;
  private String callbackStatus;
  private String callbackEventId;
  private Integer callbackAttemptCount;
  private LocalDateTime callbackLastNotifiedAt;
  private String callbackLastError;
  private Long version;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String merchantId) {
    this.merchantId = merchantId;
  }

  public String getMerchantOrderNo() {
    return merchantOrderNo;
  }

  public void setMerchantOrderNo(String merchantOrderNo) {
    this.merchantOrderNo = merchantOrderNo;
  }

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  public String getOrderType() { return orderType; }
  public void setOrderType(String value) { orderType = value; }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(String paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public String getCountry() {
    return country;
  }

  public void setCountry(String country) {
    this.country = country;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getFeeAmount() {
    return feeAmount;
  }

  public void setFeeAmount(BigDecimal feeAmount) {
    this.feeAmount = feeAmount;
  }

  public BigDecimal getNetAmount() {
    return netAmount;
  }

  public BigDecimal getPayerPayableAmount() { return payerPayableAmount; }
  public void setPayerPayableAmount(BigDecimal value) { payerPayableAmount = value; }
  public String getFeeBearer() { return feeBearer; }
  public void setFeeBearer(String value) { feeBearer = value; }

  public void setNetAmount(BigDecimal netAmount) {
    this.netAmount = netAmount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getPayoutDestinationRef() { return payoutDestinationRef; }
  public void setPayoutDestinationRef(String value) { payoutDestinationRef = value; }

  public String getMerchantRequestSnapshot() { return merchantRequestSnapshot; }
  public void setMerchantRequestSnapshot(String value) { merchantRequestSnapshot = value; }

  public String getRouteSnapshotJson() {
    return routeSnapshotJson;
  }

  public void setRouteSnapshotJson(String routeSnapshotJson) {
    this.routeSnapshotJson = routeSnapshotJson;
  }

  public String getPricingSnapshotJson() {
    return pricingSnapshotJson;
  }

  public void setPricingSnapshotJson(String pricingSnapshotJson) {
    this.pricingSnapshotJson = pricingSnapshotJson;
  }

  public LocalDateTime getExpireAt() {
    return expireAt;
  }

  public void setExpireAt(LocalDateTime expireAt) {
    this.expireAt = expireAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(LocalDateTime paidAt) {
    this.paidAt = paidAt;
  }

  public String getPaymentToken() {
    return paymentToken;
  }

  public void setPaymentToken(String paymentToken) {
    this.paymentToken = paymentToken;
  }

  public String getNotifyUrl() { return notifyUrl; }
  public void setNotifyUrl(String value) { notifyUrl = value; }
  public String getReturnUrl() { return returnUrl; }
  public void setReturnUrl(String value) { returnUrl = value; }
  public String getCustomerReference() { return customerReference; }
  public void setCustomerReference(String value) { customerReference = value; }
  public String getDescription() { return description; }
  public void setDescription(String value) { description = value; }
  public String getCallbackStatus() { return callbackStatus; }
  public void setCallbackStatus(String value) { callbackStatus = value; }
  public String getCallbackEventId() { return callbackEventId; }
  public void setCallbackEventId(String value) { callbackEventId = value; }
  public Integer getCallbackAttemptCount() { return callbackAttemptCount; }
  public void setCallbackAttemptCount(Integer value) { callbackAttemptCount = value; }
  public LocalDateTime getCallbackLastNotifiedAt() { return callbackLastNotifiedAt; }
  public void setCallbackLastNotifiedAt(LocalDateTime value) { callbackLastNotifiedAt = value; }
  public String getCallbackLastError() { return callbackLastError; }
  public void setCallbackLastError(String value) { callbackLastError = value; }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }
}
