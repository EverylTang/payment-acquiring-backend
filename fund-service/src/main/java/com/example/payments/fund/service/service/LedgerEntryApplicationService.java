package com.example.payments.fund.service.service;

import com.example.payments.fund.service.mapper.LedgerEntryMapper;
import com.example.payments.fund.service.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerEntryApplicationService {
  private final LedgerEntryMapper mapper;

  public Result recordPaymentSuccess(
      String idempotencyKey,
      String orderId,
      String merchantId,
      BigDecimal amount,
      String currency) {
    return recordPaymentSuccess(
        idempotencyKey, orderId, merchantId, amount, BigDecimal.ZERO, currency);
  }

  @Transactional
  public Result recordPaymentSuccess(
      String idempotencyKey,
      String orderId,
      String merchantId,
      BigDecimal amount,
      BigDecimal feeAmount,
      String currency) {
    validateAmount(amount);
    validateCurrency(currency);
    validateFee(amount, feeAmount);
    var existing = mapper.findByIdempotency(idempotencyKey);
    if (existing != null) {
      var result = verify(existing, orderId, merchantId, amount, currency, true);
      recordPaymentFee(orderId, merchantId, feeAmount, currency);
      return result;
    }
    var entry = new LedgerEntryEntity();
    entry.setEntryId("entry-" + orderId);
    entry.setAccountId(merchantId);
    entry.setOrderId(orderId);
    entry.setEntryType("PAYMENT_SUCCESS");
    entry.setDebitCredit("CREDIT");
    entry.setAmount(amount);
    entry.setCurrency(currency);
    entry.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
    entry.setIdempotencyKey(idempotencyKey);
    entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    try {
      mapper.insert(entry);
      recordPaymentFee(orderId, merchantId, feeAmount, currency);
      return new Result(entry, false);
    } catch (DuplicateKeyException duplicate) {
      existing = mapper.findByIdempotency(idempotencyKey);
      if (existing == null) throw duplicate;
      var result = verify(existing, orderId, merchantId, amount, currency, true);
      recordPaymentFee(orderId, merchantId, feeAmount, currency);
      return result;
    }
  }

  @Transactional
  public Result recordRefundReversal(
      String refundId, String orderId, String merchantId, BigDecimal amount, String currency) {
    validateAmount(amount);
    validateCurrency(currency);
    var key = "refund-reversal:" + refundId;
    var existing = mapper.findByIdempotency(key);
    if (existing != null)
      return verifyRefund(existing, refundId, orderId, merchantId, amount, currency);
    var original = mapper.originalPaymentAmountForUpdate(orderId);
    if (original == null)
      throw new LedgerConflictException("original payment ledger entry is missing");
    if (mapper.sumRefundReversals(orderId).add(amount).compareTo(original) > 0)
      throw new LedgerConflictException("refund reversal exceeds payment amount");
    var entry = new LedgerEntryEntity();
    entry.setEntryId("reversal-" + refundId);
    entry.setAccountId(merchantId);
    entry.setOrderId(orderId);
    entry.setRefundId(refundId);
    entry.setEntryType("REFUND_REVERSAL");
    entry.setDebitCredit("DEBIT");
    entry.setAmount(amount);
    entry.setCurrency(currency);
    entry.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
    entry.setIdempotencyKey(key);
    entry.setReversalOf("entry-" + orderId);
    entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    try {
      mapper.insert(entry);
      return new Result(entry, false);
    } catch (DuplicateKeyException duplicate) {
      var found = mapper.findByIdempotency(key);
      if (found == null) throw duplicate;
      return verifyRefund(found, refundId, orderId, merchantId, amount, currency);
    }
  }

  private void recordPaymentFee(
      String orderId, String merchantId, BigDecimal feeAmount, String currency) {
    if (feeAmount.signum() == 0) return;
    String idempotencyKey = "payment-fee:" + orderId;
    var existing = mapper.findByIdempotency(idempotencyKey);
    if (existing != null) {
      verifyFee(existing, orderId, merchantId, feeAmount, currency);
      return;
    }
    var entry = new LedgerEntryEntity();
    entry.setEntryId("fee-" + orderId);
    entry.setAccountId(merchantId);
    entry.setOrderId(orderId);
    entry.setEntryType("PAYMENT_FEE");
    entry.setDebitCredit("DEBIT");
    entry.setAmount(feeAmount);
    entry.setCurrency(currency);
    entry.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
    entry.setIdempotencyKey(idempotencyKey);
    entry.setReversalOf("entry-" + orderId);
    entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    try {
      mapper.insert(entry);
    } catch (DuplicateKeyException duplicate) {
      existing = mapper.findByIdempotency(idempotencyKey);
      if (existing == null) throw duplicate;
      verifyFee(existing, orderId, merchantId, feeAmount, currency);
    }
  }

  private static void validateFee(BigDecimal amount, BigDecimal feeAmount) {
    if (feeAmount == null || feeAmount.signum() < 0 || feeAmount.compareTo(amount) > 0) {
      throw new LedgerConflictException("payment fee is invalid");
    }
  }

  private static void validateAmount(BigDecimal amount) {
    if (amount == null || amount.signum() <= 0 || amount.scale() > 4) {
      throw new LedgerConflictException("payment amount is invalid");
    }
  }

  private static void validateCurrency(String currency) {
    if (currency == null || !currency.matches("[A-Z]{3}")) {
      throw new LedgerConflictException("payment currency is invalid");
    }
  }

  private static void verifyFee(
      LedgerEntryEntity existing,
      String orderId,
      String merchantId,
      BigDecimal feeAmount,
      String currency) {
    if (!orderId.equals(existing.getOrderId())
        || !merchantId.equals(existing.getAccountId())
        || feeAmount.compareTo(existing.getAmount()) != 0
        || !currency.equals(existing.getCurrency())
        || !"PAYMENT_FEE".equals(existing.getEntryType())
        || !"DEBIT".equals(existing.getDebitCredit())) {
      throw new LedgerConflictException("payment fee conflicts");
    }
  }

  private Result verifyRefund(
      LedgerEntryEntity existing,
      String refundId,
      String orderId,
      String merchantId,
      BigDecimal amount,
      String currency) {
    if (!refundId.equals(existing.getRefundId())
        || !orderId.equals(existing.getOrderId())
        || !merchantId.equals(existing.getAccountId())
        || amount.compareTo(existing.getAmount()) != 0
        || !currency.equals(existing.getCurrency())
        || !"REFUND_REVERSAL".equals(existing.getEntryType()))
      throw new LedgerConflictException("refund reversal conflicts");
    return new Result(existing, true);
  }

  private Result verify(
      LedgerEntryEntity existing,
      String orderId,
      String merchantId,
      BigDecimal amount,
      String currency,
      boolean duplicate) {
    if (!orderId.equals(existing.getOrderId())
        || !merchantId.equals(existing.getAccountId())
        || amount.compareTo(existing.getAmount()) != 0
        || !currency.equals(existing.getCurrency())
        || !"PAYMENT_SUCCESS".equals(existing.getEntryType())
        || !"CREDIT".equals(existing.getDebitCredit())) {
      throw new LedgerConflictException("ledger entry conflicts with payment success event");
    }
    return new Result(existing, duplicate);
  }

  public record Result(LedgerEntryEntity entry, boolean duplicate) {}

  public static class LedgerConflictException extends RuntimeException {
    public LedgerConflictException(String message) {
      super(message);
    }
  }
}
