package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.domain.PaymentAttempt;
import com.example.payments.trade.service.domain.PaymentAttemptStatus;
import com.example.payments.trade.service.mapper.PaymentAttemptRepository;
import com.example.payments.trade.service.mapper.PaymentCallbackRecordRepository;
import com.example.payments.trade.service.mapper.PaymentOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentAttemptServiceTest {

  @Test
  void doesNotChargeTheMerchantForAPayerBorneFee() {
    var order = pricedOrder("PAYER");

    assertThat(PaymentAttemptService.merchantFeeAmount(order)).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void includesAMerchantBorneFeeInTheFundsEvent() {
    var order = pricedOrder("MERCHANT");

    assertThat(PaymentAttemptService.merchantFeeAmount(order))
        .isEqualByComparingTo(new BigDecimal("2.50"));
  }

  @Test
  void queuesMerchantQueryWithoutCallingTheChannel() {
    var repository = mock(PaymentAttemptRepository.class);
    var channelConfiguration = mock(PlatformChannelConfigurationClient.class);
    var attempt =
        new PaymentAttempt(
            "attempt-1",
            "order-1",
            "channel-1",
            "channel-order-1",
            1,
            PaymentAttemptStatus.PROCESSING,
            "{}",
            "{}",
            null,
            Instant.now(),
            null,
            0,
            null,
            null);
    when(repository.findByAttemptId("attempt-1")).thenReturn(Optional.of(attempt));
    var service =
        new PaymentAttemptService(
            repository,
            mock(PaymentCallbackRecordRepository.class),
            mock(ChannelAdapterRegistry.class),
            channelConfiguration,
            mock(ChannelRequestSigner.class),
            mock(OrderService.class),
            mock(PaymentOutboxEventRepository.class),
            mock(MerchantNotificationOutboxService.class),
            mock(ExpiredPaymentSuccessExceptionService.class),
            new ObjectMapper(),
            mock(PaymentSuccessEventSigner.class));

    assertThat(service.requestQuery("attempt-1")).isEqualTo(attempt);

    verify(repository).requestImmediateQuery(org.mockito.ArgumentMatchers.eq("attempt-1"), org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(channelConfiguration);
  }

  private static PaymentOrder pricedOrder(String feeBearer) {
    return PaymentOrder.create(
            "merchant-1",
            "merchant-order-1",
            "product-1",
            "CARD",
            "US",
            "USD",
            new BigDecimal("100.00"),
            "idempotency-key",
            Instant.now().plusSeconds(3600),
            null,
            null,
            null,
            null,
            "test")
        .withPricing(
            new BigDecimal("2.50"),
            new BigDecimal("102.50"),
            new BigDecimal("100.00"),
            feeBearer,
            "{}",
            "{}");
  }
}
