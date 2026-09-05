package com.example.payments.fund.service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.payments.fund.service.mapper.*;
import com.example.payments.fund.service.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantSettlementService {
  private final MerchantSettlementDetailMapper settlementDetailMapper;
  private final MerchantSettlementRuleMapper settlementRuleMapper;
  private final MerchantSettlementBatchMapper settlementBatchMapper;
  private final MerchantFundAccountMapper fundAccountMapper;
  private final MerchantFundTransactionMapper fundTransactionMapper;
  private final SettlementDetailProcessor settlementDetailProcessor;

  public String generateSettlementBatch(LocalDate settlementDate) {
    return generateSettlementBatch(settlementDate, true);
  }

  public String generateSettlementBatch(LocalDate settlementDate, boolean automatic) {
    String batchId = "BATCH-" + UUID.randomUUID().toString();
    log.info("Starting settlement batch generation: {}, date: {}", batchId, settlementDate);

    MerchantSettlementBatchEntity batch = new MerchantSettlementBatchEntity();
    batch.setBatchId(batchId);
    batch.setSettlementDate(settlementDate);
    batch.setStatus("PROCESSING");
    batch.setStartTime(LocalDateTime.now());
    batch.setCreatedAt(LocalDateTime.now());
    batch.setUpdatedAt(LocalDateTime.now());
    settlementBatchMapper.insert(batch);

    try {
      QueryWrapper<MerchantSettlementDetailEntity> query = new QueryWrapper<>();
      query
          .eq("status", "PENDING")
          .le("expected_settlement_date", settlementDate)
          .isNull("settlement_batch_id");

      List<MerchantSettlementDetailEntity> pendingDetails =
          settlementDetailMapper.selectList(query);
      log.info("Found {} pending settlement details", pendingDetails.size());

      int successCount = 0;
      int failureCount = 0;
      BigDecimal totalAmount = BigDecimal.ZERO;
      Set<String> merchantIds = new HashSet<>();

      for (MerchantSettlementDetailEntity detail : pendingDetails) {
        if (automatic && !isEligibleForAutomaticSettlement(detail)) continue;
        try {
          var result =
              settlementDetailProcessor.settle(detail.getDetailId(), batchId, settlementDate);
          if (result.claimed()) {
            successCount++;
            totalAmount = totalAmount.add(result.detail().getSettlementAmount());
            merchantIds.add(result.detail().getMerchantId());
          }
        } catch (Exception e) {
          failureCount++;
          log.error(
              "Failed to settle detail: {}, error: {}", detail.getDetailId(), e.getMessage(), e);
          settlementDetailMapper.releaseFailedClaim(
              detail.getDetailId(), batchId, truncate(e.getMessage()), LocalDateTime.now());
        }
      }

      batch.setTotalOrders(successCount);
      batch.setTotalMerchants(merchantIds.size());
      batch.setTotalAmount(totalAmount);
      batch.setStatus(failureCount == 0 ? "COMPLETED" : "PARTIAL_FAILED");
      if (failureCount > 0) batch.setErrorMessage(failureCount + " settlement details failed");
      batch.setEndTime(LocalDateTime.now());
      batch.setUpdatedAt(LocalDateTime.now());
      settlementBatchMapper.updateById(batch);

      log.info(
          "Settlement batch completed: {}, orders: {}, merchants: {}, amount: {}",
          batchId,
          successCount,
          merchantIds.size(),
          totalAmount);
      return batchId;
    } catch (Exception e) {
      log.error("Settlement batch failed: {}, error: {}", batchId, e.getMessage(), e);
      batch.setStatus("FAILED");
      batch.setErrorMessage(e.getMessage());
      batch.setEndTime(LocalDateTime.now());
      batch.setUpdatedAt(LocalDateTime.now());
      settlementBatchMapper.updateById(batch);
      throw e;
    }
  }

  @Transactional
  public void createSettlementDetail(
      String orderId,
      String merchantId,
      BigDecimal orderAmount,
      BigDecimal feeAmount,
      String currency) {
    validateSettlementInput(orderId, merchantId, orderAmount, feeAmount, currency);
    QueryWrapper<MerchantSettlementDetailEntity> existingQuery = new QueryWrapper<>();
    existingQuery.eq("order_id", orderId);
    MerchantSettlementDetailEntity existing = settlementDetailMapper.selectOne(existingQuery);
    if (existing != null) {
      log.warn("Settlement detail already exists for order: {}", orderId);
      return;
    }

    MerchantSettlementRuleEntity rule = getActiveSettlementRule(merchantId, currency);
    if (rule == null) {
      log.error(
          "No active settlement rule found for merchant: {}, currency: {}", merchantId, currency);
      throw new RuntimeException("No settlement rule configured");
    }

    BigDecimal settlementAmount = orderAmount.subtract(feeAmount);
    LocalDate expectedDate = calculateSettlementDate(rule);

    MerchantFundAccountEntity account = getOrCreateFundAccount(merchantId, currency);

    String detailId = "SETTLE-" + UUID.randomUUID().toString();
    MerchantSettlementDetailEntity detail = new MerchantSettlementDetailEntity();
    detail.setDetailId(detailId);
    detail.setMerchantId(merchantId);
    detail.setAccountId(account.getAccountId());
    detail.setOrderId(orderId);
    detail.setOrderAmount(orderAmount);
    detail.setFeeAmount(feeAmount);
    detail.setSettlementAmount(settlementAmount);
    detail.setRefundedAmount(BigDecimal.ZERO);
    detail.setCurrency(currency);
    detail.setSettlementCycle(rule.getSettlementCycle());
    detail.setAutoSettlement(rule.getAutoSettlement());
    detail.setMinSettlementAmount(rule.getMinSettlementAmount());
    detail.setExpectedSettlementDate(expectedDate);
    detail.setStatus("PENDING");
    detail.setCreatedAt(LocalDateTime.now());
    detail.setUpdatedAt(LocalDateTime.now());

    try {
      settlementDetailMapper.insert(detail);
    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
      MerchantSettlementDetailEntity raced = settlementDetailMapper.selectOne(existingQuery);
      if (raced == null) {
        throw duplicate;
      }
      return;
    }
    log.info(
        "Created settlement detail: {}, order: {}, amount: {}, expected date: {}",
        detailId,
        orderId,
        settlementAmount,
        expectedDate);
  }

  private MerchantSettlementRuleEntity getActiveSettlementRule(String merchantId, String currency) {
    LocalDate today = LocalDate.now();
    QueryWrapper<MerchantSettlementRuleEntity> query = new QueryWrapper<>();
    query
        .eq("merchant_id", merchantId)
        .eq("currency", currency)
        .eq("status", "ACTIVE")
        .le("effective_date", today)
        .and(w -> w.isNull("expire_date").or().ge("expire_date", today))
        .orderByDesc("effective_date")
        .last("LIMIT 1");
    return settlementRuleMapper.selectOne(query);
  }

  private LocalDate calculateSettlementDate(MerchantSettlementRuleEntity rule) {
    LocalDate today = LocalDate.now();
    Integer cycleDays = rule.getCycleDays();
    if (cycleDays == null || cycleDays == 0) {
      return today;
    }
    return today.plusDays(cycleDays);
  }

  private MerchantFundAccountEntity getOrCreateFundAccount(String merchantId, String currency) {
    QueryWrapper<MerchantFundAccountEntity> query = new QueryWrapper<>();
    query.eq("merchant_id", merchantId).eq("currency", currency);
    MerchantFundAccountEntity account = fundAccountMapper.selectOne(query);

    if (account == null) {
      String accountId = "ACCT-" + UUID.randomUUID().toString();
      account = new MerchantFundAccountEntity();
      account.setAccountId(accountId);
      account.setMerchantId(merchantId);
      account.setCurrency(currency);
      account.setBalance(BigDecimal.ZERO);
      account.setFrozenBalance(BigDecimal.ZERO);
      account.setTotalIncome(BigDecimal.ZERO);
      account.setTotalExpense(BigDecimal.ZERO);
      account.setVersion(0);
      account.setStatus("ACTIVE");
      account.setCreatedAt(LocalDateTime.now());
      account.setUpdatedAt(LocalDateTime.now());
      try {
        fundAccountMapper.insert(account);
      } catch (org.springframework.dao.DuplicateKeyException duplicate) {
        account = fundAccountMapper.selectOne(query);
        if (account == null) {
          throw duplicate;
        }
      }
      log.info("Created new fund account: {} for merchant: {}", accountId, merchantId);
    }
    return account;
  }

  public MerchantSettlementBatchEntity getBatchInfo(String batchId) {
    QueryWrapper<MerchantSettlementBatchEntity> query = new QueryWrapper<>();
    query.eq("batch_id", batchId);
    return settlementBatchMapper.selectOne(query);
  }

  public List<MerchantSettlementDetailEntity> getSettlementDetailsByBatch(String batchId) {
    QueryWrapper<MerchantSettlementDetailEntity> query = new QueryWrapper<>();
    query.eq("settlement_batch_id", batchId);
    return settlementDetailMapper.selectList(query);
  }

  public MerchantFundAccountEntity getFundAccount(String merchantId, String currency) {
    QueryWrapper<MerchantFundAccountEntity> query = new QueryWrapper<>();
    query.eq("merchant_id", merchantId).eq("currency", currency);
    return fundAccountMapper.selectOne(query);
  }

  public List<MerchantFundTransactionEntity> getAccountTransactions(String accountId) {
    QueryWrapper<MerchantFundTransactionEntity> query = new QueryWrapper<>();
    query.eq("account_id", accountId).orderByDesc("created_at");
    return fundTransactionMapper.selectList(query);
  }

  @Transactional
  public MerchantSettlementRuleEntity saveRule(MerchantSettlementRuleEntity rule) {
    validateRule(rule);
    var existing =
        settlementRuleMapper.selectOne(
            new QueryWrapper<MerchantSettlementRuleEntity>()
                .eq("merchant_id", rule.getMerchantId())
                .eq("currency", rule.getCurrency())
                .eq("effective_date", rule.getEffectiveDate()));
    LocalDateTime now = LocalDateTime.now();
    if (existing == null) {
      rule.setCreatedAt(now);
      rule.setUpdatedAt(now);
      settlementRuleMapper.insert(rule);
      return rule;
    }
    rule.setId(existing.getId());
    rule.setCreatedAt(existing.getCreatedAt());
    rule.setUpdatedAt(now);
    settlementRuleMapper.updateById(rule);
    return rule;
  }

  public List<MerchantSettlementRuleEntity> listRules(String merchantId, String currency) {
    QueryWrapper<MerchantSettlementRuleEntity> query = new QueryWrapper<>();
    if (merchantId != null && !merchantId.isBlank()) query.eq("merchant_id", merchantId);
    if (currency != null && !currency.isBlank()) query.eq("currency", currency);
    return settlementRuleMapper.selectList(query.orderByDesc("effective_date"));
  }

  private boolean isEligibleForAutomaticSettlement(MerchantSettlementDetailEntity detail) {
    return Boolean.TRUE.equals(detail.getAutoSettlement())
        && detail.getSettlementAmount().compareTo(defaultAmount(detail.getMinSettlementAmount()))
            >= 0;
  }

  @Transactional
  public void applyRefund(String refundId, String orderId, BigDecimal amount, String currency) {
    if (refundId == null
        || refundId.isBlank()
        || orderId == null
        || orderId.isBlank()
        || amount == null
        || amount.signum() <= 0
        || amount.scale() > 4
        || currency == null
        || !currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("refund settlement data is invalid");
    }
    MerchantSettlementDetailEntity detail =
        settlementDetailMapper.selectOne(
            new QueryWrapper<MerchantSettlementDetailEntity>()
                .eq("order_id", orderId)
                .last("FOR UPDATE"));
    if (detail == null || "CANCELLED".equals(detail.getStatus())) return;
    if (!currency.equals(detail.getCurrency())) {
      throw new IllegalStateException("refund currency conflicts with settlement detail");
    }
    if ("PENDING".equals(detail.getStatus())) {
      String idempotencyKey = "settlement-refund:" + refundId;
      if (fundTransactionMapper.findByIdempotency(idempotencyKey) != null) return;
      MerchantFundAccountEntity account =
          fundAccountMapper.selectOne(
              new QueryWrapper<MerchantFundAccountEntity>()
                  .eq("account_id", detail.getAccountId()));
      if (account == null) throw new IllegalStateException("settlement fund account is missing");
      LocalDateTime now = LocalDateTime.now();
      BigDecimal merchantAmount = amount.min(detail.getSettlementAmount());
      MerchantFundTransactionEntity transaction =
          refundTransaction(
              refundId, orderId, detail, account, merchantAmount, currency, now, "REFUND_PENDING");
      fundTransactionMapper.insert(transaction);
      if (settlementDetailMapper.applyPendingRefund(
              detail.getDetailId(), amount, "Refund applied: " + refundId, now)
          != 1) {
        throw new IllegalStateException("refund exceeds remaining settlement amount");
      }
      return;
    }
    if (!"SETTLED".equals(detail.getStatus())) {
      throw new IllegalStateException(
          "settlement detail cannot accept a refund while " + detail.getStatus());
    }
    String idempotencyKey = "settlement-refund:" + refundId;
    if (fundTransactionMapper.findByIdempotency(idempotencyKey) != null) return;
    MerchantFundAccountEntity account =
        fundAccountMapper.selectOne(
            new QueryWrapper<MerchantFundAccountEntity>().eq("account_id", detail.getAccountId()));
    if (account == null || !"ACTIVE".equals(account.getStatus())) {
      throw new IllegalStateException("settlement fund account is unavailable");
    }
    LocalDateTime now = LocalDateTime.now();
    BigDecimal merchantAmount = amount.min(detail.getSettlementAmount());
    MerchantFundTransactionEntity transaction =
        refundTransaction(
            refundId, orderId, detail, account, merchantAmount, currency, now, "REFUND_OUT");
    fundTransactionMapper.insert(transaction);
    if (merchantAmount.signum() > 0
        && fundAccountMapper.debitForRefund(
                account.getAccountId(), merchantAmount, account.getVersion(), now)
            != 1) {
      throw new IllegalStateException("fund account changed while applying refund");
    }
    if (settlementDetailMapper.applySettledRefund(
            detail.getDetailId(), amount, "Refund applied: " + refundId, now)
        != 1) {
      throw new IllegalStateException("refund exceeds original payment amount");
    }
  }

  private static MerchantFundTransactionEntity refundTransaction(
      String refundId,
      String orderId,
      MerchantSettlementDetailEntity detail,
      MerchantFundAccountEntity account,
      BigDecimal amount,
      String currency,
      LocalDateTime now,
      String transactionType) {
    MerchantFundTransactionEntity transaction = new MerchantFundTransactionEntity();
    transaction.setTransactionId("TXN-" + UUID.randomUUID());
    transaction.setAccountId(account.getAccountId());
    transaction.setMerchantId(detail.getMerchantId());
    transaction.setTransactionType(transactionType);
    transaction.setAmount(amount.negate());
    transaction.setBalanceBefore(account.getBalance());
    transaction.setBalanceAfter(
        "REFUND_OUT".equals(transactionType)
            ? account.getBalance().subtract(amount)
            : account.getBalance());
    transaction.setCurrency(currency);
    transaction.setRelatedOrderId(orderId);
    transaction.setRelatedSettlementId(detail.getDetailId());
    transaction.setRemark("Refund " + refundId);
    transaction.setIdempotencyKey("settlement-refund:" + refundId);
    transaction.setCreatedAt(now);
    return transaction;
  }

  private static void validateSettlementInput(
      String orderId,
      String merchantId,
      BigDecimal orderAmount,
      BigDecimal feeAmount,
      String currency) {
    if (orderId == null || orderId.isBlank() || merchantId == null || merchantId.isBlank()) {
      throw new IllegalArgumentException("settlement order and merchant are required");
    }
    if (orderAmount == null || orderAmount.signum() <= 0) {
      throw new IllegalArgumentException("settlement order amount must be positive");
    }
    if (feeAmount == null || feeAmount.signum() < 0 || feeAmount.compareTo(orderAmount) > 0) {
      throw new IllegalArgumentException("settlement fee amount is invalid");
    }
    if (currency == null || !currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("settlement currency is invalid");
    }
    if (orderAmount.scale() > 4 || feeAmount.scale() > 4) {
      throw new IllegalArgumentException("settlement amount exceeds supported precision");
    }
  }

  private static void validateRule(MerchantSettlementRuleEntity rule) {
    if (rule == null
        || rule.getMerchantId() == null
        || rule.getMerchantId().isBlank()
        || rule.getCurrency() == null
        || !rule.getCurrency().matches("[A-Z]{3}")
        || rule.getEffectiveDate() == null
        || rule.getCycleDays() == null
        || rule.getCycleDays() < 0
        || rule.getMinSettlementAmount() == null
        || rule.getMinSettlementAmount().signum() < 0
        || rule.getAutoSettlement() == null
        || rule.getStatus() == null
        || !("ACTIVE".equals(rule.getStatus()) || "INACTIVE".equals(rule.getStatus()))) {
      throw new IllegalArgumentException("settlement rule is invalid");
    }
  }

  private static String truncate(String value) {
    if (value == null || value.isBlank()) return "settlement processing failed";
    return value.length() <= 512 ? value : value.substring(0, 512);
  }

  private static BigDecimal defaultAmount(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
