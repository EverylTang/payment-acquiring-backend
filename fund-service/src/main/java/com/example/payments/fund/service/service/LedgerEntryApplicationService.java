package com.example.payments.fund.service.service;

import lombok.RequiredArgsConstructor;

import com.example.payments.fund.service.mapper.LedgerEntryMapper;
import com.example.payments.fund.service.model.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LedgerEntryApplicationService {
  private final LedgerEntryMapper mapper;

  public Result recordPaymentSuccess( String idempotencyKey, String orderId, String merchantId, BigDecimal amount, String currency) {
    var existing = mapper.findByIdempotency(idempotencyKey);
    if (existing != null) return verify(existing, orderId, merchantId, amount, currency, true);
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
      return new Result(entry, false);
    } catch (DuplicateKeyException duplicate) {
      existing = mapper.findByIdempotency(idempotencyKey);
      if (existing == null) throw duplicate;
      return verify(existing, orderId, merchantId, amount, currency, true);
    }
  }

  public Result recordRefundReversal( String refundId, String orderId, String merchantId, BigDecimal amount, String currency) {
    var key = "refund-reversal:" + refundId;
    var existing = mapper.findByIdempotency(key);
    if (existing != null)
      return verifyRefund(existing, refundId, orderId, merchantId, amount, currency);
    var original = mapper.originalPaymentAmount(orderId);
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
