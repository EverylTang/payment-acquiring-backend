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
import com.example.payments.fund.service.model.MerchantFundAccountEntity;
import com.example.payments.fund.service.model.MerchantFundTransactionEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerchantFundQueryServiceTest {
  private final MerchantFundAccountMapper accountMapper =
      org.mockito.Mockito.mock(MerchantFundAccountMapper.class);
  private final MerchantFundTransactionMapper transactionMapper =
      org.mockito.Mockito.mock(MerchantFundTransactionMapper.class);
  private final MerchantFundQueryService service =
      new MerchantFundQueryService(accountMapper, transactionMapper);

  @Test
  void listAccountsNormalizesFiltersAndBoundsPagination() {
    MerchantFundAccountEntity account = new MerchantFundAccountEntity();
    account.setAccountId("account-1");
    account.setMerchantId("merchant-1");
    account.setCurrency("USD");
    account.setBalance(new BigDecimal("12.3400"));
    account.setStatus("ACTIVE");
    when(accountMapper.selectAdminPage("merchant-1", "USD", "ACTIVE", 100, 0))
        .thenReturn(List.of(account));
    when(accountMapper.countAdminPage("merchant-1", "USD", "ACTIVE")).thenReturn(1L);

    var result = service.listAccounts(0, 500, " merchant-1 ", "usd", " ACTIVE ");

    assertThat(result.page()).isEqualTo(1);
    assertThat(result.pageSize()).isEqualTo(100);
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.items()).singleElement().extracting("accountId").isEqualTo("account-1");
  }

  @Test
  void listTransactionsUsesAnExclusiveEndOfDayBoundary() {
    MerchantFundTransactionEntity transaction = new MerchantFundTransactionEntity();
    transaction.setTransactionId("transaction-1");
    transaction.setAmount(new BigDecimal("10.0000"));
    transaction.setCurrency("USD");
    LocalDate from = LocalDate.of(2026, 9, 1);
    LocalDate to = LocalDate.of(2026, 9, 6);
    LocalDateTime fromTime = from.atStartOfDay();
    LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
    when(transactionMapper.selectAdminPage(
            eq("merchant-1"),
            eq("USD"),
            eq("SETTLEMENT_IN"),
            eq(fromTime),
            eq(toExclusive),
            eq(20),
            eq(0)))
        .thenReturn(List.of(transaction));
    when(transactionMapper.countAdminPage(
            eq("merchant-1"),
            eq("USD"),
            eq("SETTLEMENT_IN"),
            eq(fromTime),
            eq(toExclusive)))
        .thenReturn(1L);

    var result =
        service.listTransactions(1, 20, "merchant-1", "USD", "SETTLEMENT_IN", from, to);

    assertThat(result.items()).singleElement().extracting("transactionId").isEqualTo("transaction-1");
    verify(transactionMapper)
        .selectAdminPage("merchant-1", "USD", "SETTLEMENT_IN", fromTime, toExclusive, 20, 0);
  }

  @Test
  void listTransactionsRejectsAnInvertedDateRangeBeforeQuerying() {
    assertThatThrownBy(
            () ->
                service.listTransactions(
                    1,
                    20,
                    "merchant-1",
                    "USD",
                    "SETTLEMENT_IN",
                    LocalDate.of(2026, 9, 7),
                    LocalDate.of(2026, 9, 6)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("明细开始日期不能晚于结束日期");

    verify(transactionMapper, never())
        .selectAdminPage(any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class));
  }
}
