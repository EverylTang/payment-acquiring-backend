package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.ArgumentCaptor;

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

  @Test
  void reusesAnOpenAttemptInsteadOfCreatingAnotherChannelCheckout() {
    var repository = mock(PaymentAttemptRepository.class);
    var openAttempt =
        new PaymentAttempt(
            "attempt-open",
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
    when(repository.findLatestOpenByOrderId("order-1")).thenReturn(Optional.of(openAttempt));
    var channelConfiguration = mock(PlatformChannelConfigurationClient.class);
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

    assertThat(service.create(pricedOrder("MERCHANT").withIdentity("order-1", com.example.payments.trade.service.domain.OrderType.PAYIN)))
        .isEqualTo(openAttempt);

    verifyNoInteractions(channelConfiguration);
  }

  @Test
  void queryUsesCredentialsAndSignatureControlsCapturedWithAttempt() {
    var repository = mock(PaymentAttemptRepository.class);
    var channelConfiguration = mock(PlatformChannelConfigurationClient.class);
    var adapters = mock(ChannelAdapterRegistry.class);
    var channel = mock(PaymentChannelAdapter.class);
    var orderService = mock(OrderService.class);
    var attempt =
        new PaymentAttempt(
            "attempt-1",
            "order-1",
            "channel-1",
            "channel-order-1",
            1,
            PaymentAttemptStatus.PROCESSING,
            """
            {"provider":"SIMULATED","requestUrl":"https://old.example.test","signatureProfile":"SIMULATED_SHA256_PREFIX_V1","schemaVersion":3,"runtimeSettings":{"signatureFields":"orderId,amount","signatureFieldName":"signature","signatureSecretRole":"callbackVerifyKey"},"runtimeCredentials":{"callbackVerifyKey":"before-rotation"}}
            """,
            "{}",
            null,
            Instant.now(),
            null,
            0,
            null,
            null);
    var rotatedRuntime =
        new ChannelRuntimeContext(
            "channel-1",
            "SIMULATED",
            "https://new.example.test",
            "SIMULATED_SHA256_PREFIX_V1",
            java.util.Map.of(),
            java.util.Map.of("callbackVerifyKey", "after-rotation"),
            4);
    when(repository.findByAttemptId("attempt-1")).thenReturn(Optional.of(attempt));
    when(channelConfiguration.resolve("channel-1")).thenReturn(rotatedRuntime);
    when(adapters.required("SIMULATED", "SIMULATED_SHA256_PREFIX_V1")).thenReturn(channel);
    when(orderService.get("order-1"))
        .thenReturn(
            pricedOrder("MERCHANT")
                .withIdentity(
                    "order-1", com.example.payments.trade.service.domain.OrderType.PAYIN));
    when(channel.queryPayment(any()))
        .thenReturn(
            new PaymentChannelAdapter.PaymentChannelResult(
                "channel-order-1", "PROCESSING", "{}", null, null, null));
    var service =
        new PaymentAttemptService(
            repository,
            mock(PaymentCallbackRecordRepository.class),
            adapters,
            channelConfiguration,
            mock(ChannelRequestSigner.class),
            orderService,
            mock(PaymentOutboxEventRepository.class),
            mock(MerchantNotificationOutboxService.class),
            mock(ExpiredPaymentSuccessExceptionService.class),
            new ObjectMapper(),
            mock(PaymentSuccessEventSigner.class));

    service.query("attempt-1");

    var request = ArgumentCaptor.forClass(PaymentChannelAdapter.PaymentChannelQuery.class);
    verify(channel).queryPayment(request.capture());
    assertThat(request.getValue().runtime().credentials())
        .containsEntry("callbackVerifyKey", "before-rotation");
    assertThat(request.getValue().runtime().settings())
        .containsEntry("signatureFields", "orderId,amount")
        .containsEntry("signatureFieldName", "signature")
        .containsEntry("signatureSecretRole", "callbackVerifyKey");
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
