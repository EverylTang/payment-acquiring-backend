package com.example.payments.fund.service.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ledger_entry")
public class LedgerEntryEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  private String entryId;
  private String accountId;
  private String orderId;
  private String refundId;
  private String entryType;
  private String debitCredit;
  private BigDecimal amount;
  private String currency;
  private LocalDateTime availableAt;
  private String idempotencyKey;
  private String reversalOf;
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEntryId() {
    return entryId;
  }

  public void setEntryId(String v) {
    entryId = v;
  }

  public String getAccountId() {
    return accountId;
  }

  public void setAccountId(String v) {
    accountId = v;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String v) {
    orderId = v;
  }

  public String getRefundId() {
    return refundId;
  }

  public void setRefundId(String v) {
    refundId = v;
  }

  public String getEntryType() {
    return entryType;
  }

  public void setEntryType(String v) {
    entryType = v;
  }

  public String getDebitCredit() {
    return debitCredit;
  }

  public void setDebitCredit(String v) {
    debitCredit = v;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal v) {
    amount = v;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String v) {
    currency = v;
  }

  public LocalDateTime getAvailableAt() {
    return availableAt;
  }

  public void setAvailableAt(LocalDateTime v) {
    availableAt = v;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String v) {
    idempotencyKey = v;
  }

  public String getReversalOf() {
    return reversalOf;
  }

  public void setReversalOf(String v) {
    reversalOf = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
