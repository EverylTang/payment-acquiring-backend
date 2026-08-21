package com.example.payments.fund.service.application;

import com.example.payments.fund.service.infrastructure.persistence.LedgerEntryEntity;
import com.example.payments.fund.service.infrastructure.persistence.LedgerEntryMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class LedgerEntryApplicationService {
  private final LedgerEntryMapper mapper;

  public LedgerEntryApplicationService(LedgerEntryMapper mapper) { this.mapper = mapper; }

  public Result recordPaymentSuccess(String idempotencyKey, String orderId, String merchantId,
      BigDecimal amount, String currency) {
    var existing = mapper.findByIdempotency(idempotencyKey);
    if (existing != null) return verify(existing, orderId, merchantId, amount, currency, true);
    var entry = new LedgerEntryEntity();
    entry.setEntryId("entry-" + orderId); entry.setAccountId(merchantId); entry.setOrderId(orderId);
    entry.setEntryType("PAYMENT_SUCCESS"); entry.setDebitCredit("CREDIT"); entry.setAmount(amount);
    entry.setCurrency(currency); entry.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
    entry.setIdempotencyKey(idempotencyKey); entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    try { mapper.insert(entry); return new Result(entry, false); }
    catch (DuplicateKeyException duplicate) {
      existing = mapper.findByIdempotency(idempotencyKey);
      if (existing == null) throw duplicate;
      return verify(existing, orderId, merchantId, amount, currency, true);
    }
  }

  private Result verify(LedgerEntryEntity existing, String orderId, String merchantId,
      BigDecimal amount, String currency, boolean duplicate) {
    if (!orderId.equals(existing.getOrderId()) || !merchantId.equals(existing.getAccountId())
        || amount.compareTo(existing.getAmount()) != 0 || !currency.equals(existing.getCurrency())
        || !"PAYMENT_SUCCESS".equals(existing.getEntryType()) || !"CREDIT".equals(existing.getDebitCredit())) {
      throw new LedgerConflictException("ledger entry conflicts with payment success event");
    }
    return new Result(existing, duplicate);
  }

  public record Result(LedgerEntryEntity entry, boolean duplicate) {}
  public static class LedgerConflictException extends RuntimeException {
    public LedgerConflictException(String message) { super(message); }
  }
}
