package com.example.payments.fund.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.fund.service.mapper.MerchantFundAccountMapper;
import com.example.payments.fund.service.mapper.MerchantFundTransactionMapper;
import com.example.payments.fund.service.mapper.MerchantSettlementDetailMapper;
import com.example.payments.fund.service.model.MerchantFundAccountEntity;
import com.example.payments.fund.service.model.MerchantFundTransactionEntity;
import com.example.payments.fund.service.model.MerchantSettlementDetailEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SettlementDetailProcessorTest {
  private final MerchantSettlementDetailMapper detailMapper =
      org.mockito.Mockito.mock(MerchantSettlementDetailMapper.class);
  private final MerchantFundAccountMapper accountMapper =
      org.mockito.Mockito.mock(MerchantFundAccountMapper.class);
  private final MerchantFundTransactionMapper transactionMapper =
      org.mockito.Mockito.mock(MerchantFundTransactionMapper.class);
  private final SettlementDetailProcessor processor =
      new SettlementDetailProcessor(detailMapper, accountMapper, transactionMapper);

  @Test
  void claimsAndSettlesDetailWithVersionCheckedBalanceUpdate() {
    MerchantSettlementDetailEntity detail = detail();
    MerchantFundAccountEntity account = account();
    when(detailMapper.claimForSettlement(eq("detail-1"), eq("batch-1"), any(), any())).thenReturn(1);
    when(detailMapper.selectOne(any())).thenReturn(detail);
    when(accountMapper.selectOne(any())).thenReturn(account);
    when(accountMapper.creditForSettlement(
            eq("account-1"), eq(new BigDecimal("9.75")), eq(3), any(LocalDateTime.class)))
        .thenReturn(1);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              invocation.<MerchantFundTransactionEntity>getArgument(0).setId(42L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(MerchantFundTransactionEntity.class));
    when(detailMapper.markSettled(eq("detail-1"), eq("batch-1"), any(), eq(42L), any()))
        .thenReturn(1);

    var result = processor.settle("detail-1", "batch-1", LocalDate.of(2026, 9, 5));

    assertThat(result.claimed()).isTrue();
    verify(transactionMapper).insert(any(MerchantFundTransactionEntity.class));
    verify(detailMapper).markSettled(eq("detail-1"), eq("batch-1"), any(), eq(42L), any());
  }

  @Test
  void doesNotMutateFundsWhenAnotherWorkerAlreadyClaimedTheDetail() {
    when(detailMapper.claimForSettlement(eq("detail-1"), eq("batch-1"), any(), any())).thenReturn(0);

    var result = processor.settle("detail-1", "batch-1", LocalDate.of(2026, 9, 5));

    assertThat(result.claimed()).isFalse();
    org.mockito.Mockito.verifyNoInteractions(accountMapper, transactionMapper);
  }

  private static MerchantSettlementDetailEntity detail() {
    MerchantSettlementDetailEntity detail = new MerchantSettlementDetailEntity();
    detail.setDetailId("detail-1");
    detail.setAccountId("account-1");
    detail.setMerchantId("merchant-1");
    detail.setOrderId("order-1");
    detail.setCurrency("USD");
    detail.setSettlementAmount(new BigDecimal("9.75"));
    return detail;
  }

  private static MerchantFundAccountEntity account() {
    MerchantFundAccountEntity account = new MerchantFundAccountEntity();
    account.setAccountId("account-1");
    account.setBalance(new BigDecimal("100.00"));
    account.setVersion(3);
    account.setStatus("ACTIVE");
    return account;
  }
}
