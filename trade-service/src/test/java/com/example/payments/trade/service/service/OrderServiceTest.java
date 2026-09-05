package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.payments.trade.service.config.OrderExpirationProperties;
import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceTest {
  private final PaymentOrderRepository repository =
      org.mockito.Mockito.mock(PaymentOrderRepository.class);
  private final OrderService service = new OrderService(repository);

  @Test
  void duplicateMerchantOrderReturnsExistingOrder() {
    var command =
        new OrderService.CreateOrderCommand(
            "m1", "o1", "p1", "CARD", "US", "USD", new BigDecimal("10.00"), "key-1", null, null, null, null, null, null);
    var existing =
        PaymentOrder.create(
            "m1",
            "o1",
            "p1",
            "CARD",
            "US",
            "USD",
            new BigDecimal("10.00"),
            "key-1",
            command.expireAt(), null, null, null, null, null);
    when(repository.findByIdempotency("m1", "key-1", "PAYIN")).thenReturn(Optional.of(existing));
    assertThat(service.create(command).orderId()).isEqualTo(existing.orderId());
  }

  @Test
  void terminalSuccessCannotBeRolledBackByFailureCallback() {
    var success =
        PaymentOrder.create(
                "m1",
                "o2",
                "p1",
                "CARD",
                "US",
                "USD",
                new BigDecimal("10.00"),
                "key-2",
                java.time.Instant.now().plusSeconds(1800), null, null, null, null, null)
            .withStatus(OrderStatus.SUCCESS, java.time.Instant.now());
    when(repository.findById("o2")).thenReturn(Optional.of(success));
    assertThat(service.callback("o2", OrderStatus.FAILED).status()).isEqualTo(OrderStatus.SUCCESS);
  }

  @Test
  void createsOrderUsingTheResolvedChannelPricingSnapshot() {
    var channelConfiguration = org.mockito.Mockito.mock(PlatformChannelConfigurationClient.class);
    var configuredService = new OrderService(repository, channelConfiguration, new ObjectMapper());
    var command =
        new OrderService.CreateOrderCommand(
            "m2", "o3", "p1", "CARD", "US", "USD", new BigDecimal("10.00"), "key-3", null, null, null, null, null, null);
    var runtime =
        new ChannelRuntimeContext(
            "payermax-card-us",
            "PAYERMAX",
            "https://payments.example.test",
            "HMAC_SHA256_V1",
            Map.of(),
            Map.of());
    var configuration =
        new PlatformChannelConfigurationClient.ResolvedPaymentConfiguration(
            runtime,
            "price-payermax-usd",
            new BigDecimal("0.025"),
            new BigDecimal("0.30"),
            "INCLUSIVE",
            "12");
    when(repository.findByIdempotency("m2", "key-3", "PAYIN")).thenReturn(Optional.empty());
    when(repository.findByMerchantOrder("m2", "o3", "PAYIN")).thenReturn(Optional.empty());
    when(channelConfiguration.productType("p1")).thenReturn("PAYIN");
    when(channelConfiguration.resolveConfiguration(any())).thenReturn(configuration);
    when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var order = configuredService.create(command);

    assertThat(order.feeAmount()).isEqualByComparingTo("0.55");
    assertThat(order.netAmount()).isEqualByComparingTo("9.45");
    assertThat(order.routeSnapshot()).contains("payermax-card-us");
    assertThat(order.pricingSnapshot()).contains("price-payermax-usd");
  }

  @Test
  void createsPayoutWithAnIndependentIdempotencyNamespace() {
    var channelConfiguration = org.mockito.Mockito.mock(PlatformChannelConfigurationClient.class);
    var configuredService = new OrderService(repository, channelConfiguration, new ObjectMapper());
    var command = new OrderService.CreateOrderCommand(
        "m2", "shared-order", "payout-usd", "BANK", "US", "USD", new BigDecimal("10.00"),
        "shared-key", null, null, null, null, "beneficiary-ref", null);
    var runtime = new ChannelRuntimeContext(
        "payout-bank-us", "SIMULATED", "https://payments.example.test", "HMAC_SHA256_V1", Map.of(), Map.of());
    var configuration = new PlatformChannelConfigurationClient.ResolvedPaymentConfiguration(
        runtime, "price-payout-usd", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null,
        "COMBINED", List.of(), "EXCLUSIVE", "14", null, "PASS", "PAYOUT");
    when(repository.findByIdempotency("m2", "shared-key", "PAYOUT")).thenReturn(Optional.empty());
    when(repository.findByMerchantOrder("m2", "shared-order", "PAYOUT")).thenReturn(Optional.empty());
    when(channelConfiguration.productType("payout-usd")).thenReturn("PAYOUT");
    when(channelConfiguration.resolveConfiguration(any())).thenReturn(configuration);
    when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var order = configuredService.create(command);

    assertThat(order.orderType().name()).isEqualTo("PAYOUT");
    assertThat(order.orderId()).startsWith("PO");
  }

  @Test
  void rejectsPayoutWithoutDestinationReference() {
    var channelConfiguration = org.mockito.Mockito.mock(PlatformChannelConfigurationClient.class);
    var configuredService = new OrderService(repository, channelConfiguration, new ObjectMapper());
    var command = new OrderService.CreateOrderCommand(
        "m2", "payout-missing-destination", "payout-usd", "BANK", "US", "USD",
        new BigDecimal("10.00"), "missing-destination-key", null, null, null, null, null, null);
    when(channelConfiguration.productType("payout-usd")).thenReturn("PAYOUT");
    when(repository.findByIdempotency("m2", "missing-destination-key", "PAYOUT")).thenReturn(Optional.empty());
    when(repository.findByMerchantOrder("m2", "payout-missing-destination", "PAYOUT")).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class, () -> configuredService.create(command));
  }

  @Test
  void calculatesTheFeeFromTheMatchedTier() {
    var channelConfiguration = org.mockito.Mockito.mock(PlatformChannelConfigurationClient.class);
    var configuredService = new OrderService(repository, channelConfiguration, new ObjectMapper());
    var command =
        new OrderService.CreateOrderCommand(
            "m3", "o4", "p1", "CARD", "US", "USD", new BigDecimal("150.00"), "key-4", null, null, null, null, null, null);
    var runtime =
        new ChannelRuntimeContext(
            "antom-card-us", "ANTOM", "https://payments.example.test", "HMAC_SHA256_V1", Map.of(), Map.of());
    var configuration =
        new PlatformChannelConfigurationClient.ResolvedPaymentConfiguration(
            runtime,
            "price-tiered-usd",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("0.20"),
            new BigDecimal("2.50"),
            new BigDecimal("4.00"),
            "TIERED",
            List.of(
                new PlatformChannelConfigurationClient.FeeTier(BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("0.02"), BigDecimal.ZERO),
                new PlatformChannelConfigurationClient.FeeTier(new BigDecimal("100.01"), new BigDecimal("1000.00"), new BigDecimal("0.01"), new BigDecimal("0.50"))),
            "EXCLUSIVE",
            "13");
    when(repository.findByIdempotency("m3", "key-4", "PAYIN")).thenReturn(Optional.empty());
    when(repository.findByMerchantOrder("m3", "o4", "PAYIN")).thenReturn(Optional.empty());
    when(channelConfiguration.productType("p1")).thenReturn("PAYIN");
    when(channelConfiguration.resolveConfiguration(any())).thenReturn(configuration);
    when(repository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var order = configuredService.create(command);

    assertThat(order.feeAmount()).isEqualByComparingTo("2.50");
    assertThat(order.payerPayableAmount()).isEqualByComparingTo("152.50");
    assertThat(order.netAmount()).isEqualByComparingTo("150.00");
    assertThat(order.pricingSnapshot()).contains("TIERED");
  }

  @Test
  void returnsStatisticsFromTheRepositoryAggregate() {
    when(repository.statistics())
        .thenReturn(
            new PaymentOrderRepository.OrderStatistics(
                3, 2, new BigDecimal("125.50"), 2));

    var statistics = service.statistics();

    assertThat(statistics)
        .containsEntry("totalOrders", 3L)
        .containsEntry("successfulOrders", 2L)
        .containsEntry("paymentSuccessRate", new BigDecimal("66.67"))
        .containsEntry("paymentVolume", new BigDecimal("125.50"))
        .containsEntry("activeMerchants", 2L);
  }

  @Test
  void rejectsMalformedNotificationUrlBeforePersistingTheOrder() {
    var command =
        new OrderService.CreateOrderCommand(
            "m4", "o5", "p1", "CARD", "US", "USD", new BigDecimal("10.00"), "key-5", null,
            "javascript:alert(1)", null, null, null, null);
    when(repository.findByIdempotency("m4", "key-5", "PAYIN")).thenReturn(Optional.empty());
    when(repository.findByMerchantOrder("m4", "o5", "PAYIN")).thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class, () -> service.create(command));
  }

  @Test
  void rejectsOrderOutsideTheConfiguredValidityWindow() {
    var command =
        new OrderService.CreateOrderCommand(
            "m5",
            "expires-too-soon",
            "p1",
            "CARD",
            "US",
            "USD",
            new BigDecimal("10.00"),
            "key-6",
            Instant.now().plusSeconds(30),
            null,
            null,
            null,
            null,
            null);
    when(repository.findByIdempotency("m5", "key-6", "PAYIN")).thenReturn(Optional.empty());
    when(repository.findByMerchantOrder("m5", "expires-too-soon", "PAYIN"))
        .thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        ResponseStatusException.class, () -> service.create(command));
  }

  @Test
  void successfulCallbackAfterExpirationDoesNotRestoreTheOrder() {
    var active =
        PaymentOrder.create(
            "m6",
            "expired-callback",
            "p1",
            "CARD",
            "US",
            "USD",
            new BigDecimal("10.00"),
            "key-7",
            Instant.now().minusSeconds(1),
            null,
            null,
            null,
            null,
            null);
    var expired = active.withStatus(OrderStatus.EXPIRED, null);
    when(repository.findById(active.orderId()))
        .thenReturn(Optional.of(active), Optional.of(expired));
    when(repository.expire(
            org.mockito.ArgumentMatchers.eq(active.orderId()),
            org.mockito.ArgumentMatchers.eq(OrderStatus.CREATED),
            any()))
        .thenReturn(true);

    assertThat(service.callback(active.orderId(), OrderStatus.SUCCESS).status())
        .isEqualTo(OrderStatus.EXPIRED);
  }

  @Test
  void expirationSweepTransitionsOnlyEligibleOrders() {
    var due =
        PaymentOrder.create(
            "m7",
            "sweep-due",
            "p1",
            "CARD",
            "US",
            "USD",
            new BigDecimal("10.00"),
            "key-8",
            Instant.now().minusSeconds(1),
            null,
            null,
            null,
            null,
            null);
    when(repository.findExpirable(any(), org.mockito.ArgumentMatchers.eq(10)))
        .thenReturn(List.of(due));
    when(repository.expire(
            org.mockito.ArgumentMatchers.eq(due.orderId()),
            org.mockito.ArgumentMatchers.eq(OrderStatus.CREATED),
            any()))
        .thenReturn(true);

    assertThat(service.expireDue(Instant.now(), 10)).isEqualTo(1);
  }

  @Test
  void expirationSweepEnqueuesMerchantExpirationNotification() {
    var notificationOutbox = org.mockito.Mockito.mock(MerchantNotificationOutboxService.class);
    var notifyingService =
        new OrderService(
            repository,
            null,
            null,
            new OrderNumberGenerator(0, System::currentTimeMillis),
            new MerchantCallbackUrlPolicy(false),
            notificationOutbox,
            new OrderExpirationProperties(10, 60, 86400));
    var due =
        PaymentOrder.create(
            "m8",
            "sweep-notify",
            "p1",
            "CARD",
            "US",
            "USD",
            new BigDecimal("10.00"),
            "key-9",
            Instant.now().minusSeconds(1),
            "https://merchant.example/notify",
            null,
            null,
            null,
            null);
    when(repository.findExpirable(any(), org.mockito.ArgumentMatchers.eq(10)))
        .thenReturn(List.of(due));
    when(repository.expire(
            org.mockito.ArgumentMatchers.eq(due.orderId()),
            org.mockito.ArgumentMatchers.eq(OrderStatus.CREATED),
            any()))
        .thenReturn(true);

    notifyingService.expireDue(Instant.now(), 10);

    verify(notificationOutbox).enqueueOrderExpired(any(PaymentOrder.class));
  }
}
