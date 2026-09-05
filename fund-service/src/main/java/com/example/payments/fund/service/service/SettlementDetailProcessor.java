package com.example.payments.fund.service.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.payments.fund.service.mapper.MerchantFundAccountMapper;
import com.example.payments.fund.service.mapper.MerchantFundTransactionMapper;
import com.example.payments.fund.service.mapper.MerchantSettlementDetailMapper;
import com.example.payments.fund.service.model.MerchantFundAccountEntity;
import com.example.payments.fund.service.model.MerchantFundTransactionEntity;
import com.example.payments.fund.service.model.MerchantSettlementDetailEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementDetailProcessor {
  private final MerchantSettlementDetailMapper settlementDetailMapper;
  private final MerchantFundAccountMapper fundAccountMapper;
  private final MerchantFundTransactionMapper fundTransactionMapper;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public SettlementResult settle(String detailId, String batchId, LocalDate settlementDate) {
    LocalDateTime now = LocalDateTime.now();
    if (settlementDetailMapper.claimForSettlement(detailId, batchId, settlementDate, now) != 1) {
      return SettlementResult.notClaimed();
    }

    MerchantSettlementDetailEntity detail =
        settlementDetailMapper.selectOne(
            new QueryWrapper<MerchantSettlementDetailEntity>().eq("detail_id", detailId));
    if (detail == null) {
      throw new IllegalStateException("claimed settlement detail is missing: " + detailId);
    }
    MerchantFundAccountEntity account =
        fundAccountMapper.selectOne(
            new QueryWrapper<MerchantFundAccountEntity>().eq("account_id", detail.getAccountId()));
    if (account == null) {
      throw new IllegalStateException("fund account not found: " + detail.getAccountId());
    }
    if (!"ACTIVE".equals(account.getStatus())) {
      throw new IllegalStateException("fund account is not active: " + account.getAccountId());
    }

    BigDecimal balanceBefore = account.getBalance();
    BigDecimal balanceAfter = balanceBefore.add(detail.getSettlementAmount());
    if (fundAccountMapper.creditForSettlement(
            account.getAccountId(), detail.getSettlementAmount(), account.getVersion(), now)
        != 1) {
      throw new IllegalStateException(
          "fund account changed while settling: " + account.getAccountId());
    }

    MerchantFundTransactionEntity transaction = new MerchantFundTransactionEntity();
    transaction.setTransactionId("TXN-" + UUID.randomUUID());
    transaction.setAccountId(account.getAccountId());
    transaction.setMerchantId(detail.getMerchantId());
    transaction.setTransactionType("SETTLEMENT_IN");
    transaction.setAmount(detail.getSettlementAmount());
    transaction.setBalanceBefore(balanceBefore);
    transaction.setBalanceAfter(balanceAfter);
    transaction.setCurrency(detail.getCurrency());
    transaction.setRelatedOrderId(detail.getOrderId());
    transaction.setRelatedSettlementId(detail.getDetailId());
    transaction.setIdempotencyKey("settle-" + detail.getDetailId());
    transaction.setCreatedAt(now);
    fundTransactionMapper.insert(transaction);

    if (settlementDetailMapper.markSettled(
            detailId, batchId, settlementDate, transaction.getId(), now)
        != 1) {
      throw new IllegalStateException("settlement detail claim was lost: " + detailId);
    }
    return SettlementResult.settled(detail);
  }

  public record SettlementResult(boolean claimed, MerchantSettlementDetailEntity detail) {
    static SettlementResult notClaimed() {
      return new SettlementResult(false, null);
    }

    static SettlementResult settled(MerchantSettlementDetailEntity detail) {
      return new SettlementResult(true, detail);
    }
  }
}
