package com.example.payments.fund.service.service;

import com.example.payments.fund.service.mapper.MerchantFundAccountMapper;
import com.example.payments.fund.service.mapper.MerchantFundTransactionMapper;
import com.example.payments.fund.service.model.MerchantFundAccountEntity;
import com.example.payments.fund.service.model.MerchantFundTransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantFundQueryService {
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final MerchantFundAccountMapper accountMapper;
  private final MerchantFundTransactionMapper transactionMapper;

  public Page<AccountView> listAccounts(
      int page, int pageSize, String merchantId, String currency, String status) {
    PageRequest request = pageRequest(page, pageSize);
    String normalizedMerchantId = normalize(merchantId);
    String normalizedCurrency = normalizeCurrency(currency);
    String normalizedStatus = normalize(status);
    List<AccountView> items =
        accountMapper
            .selectAdminPage(
                normalizedMerchantId,
                normalizedCurrency,
                normalizedStatus,
                request.pageSize(),
                request.offset())
            .stream()
            .map(this::accountView)
            .toList();
    long total =
        accountMapper.countAdminPage(normalizedMerchantId, normalizedCurrency, normalizedStatus);
    return new Page<>(items, request.page(), request.pageSize(), total);
  }

  public Page<TransactionView> listTransactions(
      int page,
      int pageSize,
      String merchantId,
      String currency,
      String transactionType,
      LocalDate createdFrom,
      LocalDate createdTo) {
    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new IllegalArgumentException("明细开始日期不能晚于结束日期");
    }
    PageRequest request = pageRequest(page, pageSize);
    LocalDateTime from = createdFrom == null ? null : createdFrom.atStartOfDay();
    LocalDateTime toExclusive = createdTo == null ? null : createdTo.plusDays(1).atStartOfDay();
    String normalizedCurrency = normalizeCurrency(currency);
    String normalizedMerchantId = normalize(merchantId);
    String normalizedType = normalize(transactionType);
    List<TransactionView> items =
        transactionMapper
            .selectAdminPage(
                normalizedMerchantId,
                normalizedCurrency,
                normalizedType,
                from,
                toExclusive,
                request.pageSize(),
                request.offset())
            .stream()
            .map(this::transactionView)
            .toList();
    long total =
        transactionMapper.countAdminPage(
            normalizedMerchantId, normalizedCurrency, normalizedType, from, toExclusive);
    return new Page<>(items, request.page(), request.pageSize(), total);
  }

  private PageRequest pageRequest(int page, int pageSize) {
    int safePage = Math.max(page, 1);
    int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    if (pageSize <= 0) safePageSize = DEFAULT_PAGE_SIZE;
    return new PageRequest(safePage, safePageSize, (safePage - 1) * safePageSize);
  }

  private String normalizeCurrency(String currency) {
    String value = normalize(currency);
    if (value == null) return null;
    String normalized = value.toUpperCase(java.util.Locale.ROOT);
    if (!normalized.matches("[A-Z]{3}")) throw new IllegalArgumentException("币种必须为三位大写代码");
    return normalized;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.trim();
  }

  private AccountView accountView(MerchantFundAccountEntity account) {
    return new AccountView(
        account.getAccountId(),
        account.getMerchantId(),
        account.getCurrency(),
        account.getBalance(),
        account.getFrozenBalance(),
        account.getTotalIncome(),
        account.getTotalExpense(),
        account.getStatus(),
        account.getUpdatedAt());
  }

  private TransactionView transactionView(MerchantFundTransactionEntity transaction) {
    return new TransactionView(
        transaction.getTransactionId(),
        transaction.getAccountId(),
        transaction.getMerchantId(),
        transaction.getTransactionType(),
        transaction.getAmount(),
        transaction.getBalanceBefore(),
        transaction.getBalanceAfter(),
        transaction.getCurrency(),
        transaction.getRelatedOrderId(),
        transaction.getRelatedSettlementId(),
        transaction.getRemark(),
        transaction.getCreatedAt());
  }

  private record PageRequest(int page, int pageSize, int offset) {}

  public record Page<T>(List<T> items, int page, int pageSize, long total) {}

  public record AccountView(
      String accountId,
      String merchantId,
      String currency,
      BigDecimal balance,
      BigDecimal frozenBalance,
      BigDecimal totalIncome,
      BigDecimal totalExpense,
      String status,
      LocalDateTime updatedAt) {}

  public record TransactionView(
      String transactionId,
      String accountId,
      String merchantId,
      String transactionType,
      BigDecimal amount,
      BigDecimal balanceBefore,
      BigDecimal balanceAfter,
      String currency,
      String relatedOrderId,
      String relatedSettlementId,
      String remark,
      LocalDateTime createdAt) {}
}
