package com.example.payments.fund.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.fund.service.mapper.MerchantFundAccountMapper;
import com.example.payments.fund.service.mapper.MerchantFundTransactionMapper;
import com.example.payments.fund.service.mapper.MerchantSettlementBatchMapper;
import com.example.payments.fund.service.mapper.MerchantSettlementDetailMapper;
import com.example.payments.fund.service.mapper.MerchantSettlementRuleMapper;
import com.example.payments.fund.service.model.MerchantFundAccountEntity;
import com.example.payments.fund.service.model.MerchantFundTransactionEntity;
import com.example.payments.fund.service.model.MerchantSettlementDetailEntity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MerchantSettlementServiceTest {
  private final MerchantSettlementDetailMapper detailMapper =
      org.mockito.Mockito.mock(MerchantSettlementDetailMapper.class);
  private final MerchantSettlementRuleMapper ruleMapper =
      org.mockito.Mockito.mock(MerchantSettlementRuleMapper.class);
  private final MerchantSettlementBatchMapper batchMapper =
      org.mockito.Mockito.mock(MerchantSettlementBatchMapper.class);
  private final MerchantFundAccountMapper accountMapper =
      org.mockito.Mockito.mock(MerchantFundAccountMapper.class);
  private final MerchantFundTransactionMapper transactionMapper =
      org.mockito.Mockito.mock(MerchantFundTransactionMapper.class);
  private final MerchantSettlementService service =
      new MerchantSettlementService(
          detailMapper,
          ruleMapper,
          batchMapper,
          accountMapper,
          transactionMapper,
          org.mockito.Mockito.mock(SettlementDetailProcessor.class));

  @Test
  void settledFullRefundOnlyDebitsMerchantNetSettlementAmount() {
    MerchantSettlementDetailEntity detail = settledDetail();
    MerchantFundAccountEntity account = account();
    when(detailMapper.selectOne(any())).thenReturn(detail);
    when(transactionMapper.findByIdempotency("settlement-refund:refund-1")).thenReturn(null);
    when(accountMapper.selectOne(any())).thenReturn(account);
    when(accountMapper.debitForRefund(
            eq("account-1"), eq(new BigDecimal("90.0000")), eq(3), any(LocalDateTime.class)))
        .thenReturn(1);
    when(detailMapper.applySettledRefund(
            eq("detail-1"), eq(new BigDecimal("100.0000")), any(), any(LocalDateTime.class)))
        .thenReturn(1);

    service.applyRefund("refund-1", "order-1", new BigDecimal("100.0000"), "USD");

    ArgumentCaptor<MerchantFundTransactionEntity> transaction =
        ArgumentCaptor.forClass(MerchantFundTransactionEntity.class);
    verify(transactionMapper).insert(transaction.capture());
    assertThat(transaction.getValue().getAmount()).isEqualByComparingTo("-90.0000");
    assertThat(transaction.getValue().getBalanceAfter()).isEqualByComparingTo("10.0000");
    verify(accountMapper)
        .debitForRefund(eq("account-1"), eq(new BigDecimal("90.0000")), eq(3), any());
    verify(detailMapper)
        .applySettledRefund(eq("detail-1"), eq(new BigDecimal("100.0000")), any(), any());
  }

  @Test
  void duplicateRefundDoesNotDebitMerchantAgain() {
    when(detailMapper.selectOne(any())).thenReturn(settledDetail());
    MerchantFundTransactionEntity existing = new MerchantFundTransactionEntity();
    when(transactionMapper.findByIdempotency("settlement-refund:refund-1")).thenReturn(existing);

    service.applyRefund("refund-1", "order-1", new BigDecimal("10.0000"), "USD");

    verify(accountMapper, never()).debitForRefund(any(), any(), any(), any());
    verify(detailMapper, never()).applySettledRefund(any(), any(), any(), any());
  }

  @Test
  void rejectsRefundAmountsBeyondSupportedPrecisionBeforeAccessingFunds() {
    assertThatThrownBy(
            () -> service.applyRefund("refund-1", "order-1", new BigDecimal("1.00001"), "USD"))
        .isInstanceOf(IllegalArgumentException.class);

    verify(detailMapper, never()).selectOne(any());
  }

  private static MerchantSettlementDetailEntity settledDetail() {
    MerchantSettlementDetailEntity detail = new MerchantSettlementDetailEntity();
    detail.setDetailId("detail-1");
    detail.setAccountId("account-1");
    detail.setOrderId("order-1");
    detail.setMerchantId("merchant-1");
    detail.setCurrency("USD");
    detail.setStatus("SETTLED");
    detail.setOrderAmount(new BigDecimal("100.0000"));
    detail.setFeeAmount(new BigDecimal("10.0000"));
    detail.setSettlementAmount(new BigDecimal("90.0000"));
    detail.setRefundedAmount(BigDecimal.ZERO);
    return detail;
  }

  private static MerchantFundAccountEntity account() {
    MerchantFundAccountEntity account = new MerchantFundAccountEntity();
    account.setAccountId("account-1");
    account.setBalance(new BigDecimal("100.0000"));
    account.setVersion(3);
    account.setStatus("ACTIVE");
    return account;
  }
}
