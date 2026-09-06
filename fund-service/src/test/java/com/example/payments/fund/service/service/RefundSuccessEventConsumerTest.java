package com.example.payments.fund.service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.fund.service.mapper.LedgerEntryMapper;
import com.example.payments.fund.service.mapper.RefundEventConsumptionMapper;
import com.example.payments.fund.service.model.LedgerEntryEntity;
import com.example.payments.fund.service.model.RefundEventConsumptionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RefundSuccessEventConsumerTest {
  private static final String SIGNING_SECRET = "test-refund-success-event-secret";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final LedgerEntryMapper ledgerMapper = org.mockito.Mockito.mock(LedgerEntryMapper.class);
  private final RefundEventConsumptionMapper consumptionMapper =
      org.mockito.Mockito.mock(RefundEventConsumptionMapper.class);
  private final MerchantSettlementService settlementService =
      org.mockito.Mockito.mock(MerchantSettlementService.class);
  private final RefundSuccessEventConsumer consumer =
      new RefundSuccessEventConsumer(
          new LedgerEntryApplicationService(ledgerMapper),
          settlementService,
          OBJECT_MAPPER,
          consumptionMapper,
          new SimpleMeterRegistry(),
          SIGNING_SECRET);

  @Test
  void recordsAValidSignedRefundEvent() {
    when(ledgerMapper.originalPaymentAmountForUpdate("order-1"))
        .thenReturn(new BigDecimal("10.2500"));
    when(ledgerMapper.sumRefundReversals("order-1")).thenReturn(BigDecimal.ZERO);

    consumer.onMessage(event());

    verify(ledgerMapper).insert(any(LedgerEntryEntity.class));
    verify(consumptionMapper).insert(any(RefundEventConsumptionEntity.class));
    verify(settlementService)
        .applyRefund("refund-1", "order-1", new BigDecimal("2.125"), "USD");
  }

  @Test
  void rejectsTamperedRefundEventBeforeFundsAreRecorded() {
    assertThatThrownBy(() -> consumer.onMessage(event().replace("merchant-1", "merchant-2")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid refund success event signature");
  }

  @Test
  void zeroAmountEventIsRejectedBeforeAnyFundWrite() {
    assertThatThrownBy(() -> consumer.onMessage(event(BigDecimal.ZERO)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid refund amount");

    org.mockito.Mockito.verifyNoInteractions(ledgerMapper, consumptionMapper, settlementService);
  }

  @Test
  void invalidCurrencyEventIsRejectedBeforeAnyFundWrite() {
    assertThatThrownBy(() -> consumer.onMessage(event(new BigDecimal("2.125"), "usd")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid refund currency");

    org.mockito.Mockito.verifyNoInteractions(ledgerMapper, consumptionMapper, settlementService);
  }

  private static String event() {
    return event(new BigDecimal("2.125"));
  }

  private static String event(BigDecimal amount) {
    return event(amount, "USD");
  }

  private static String event(BigDecimal amount, String currency) {
    try {
      ObjectNode event = OBJECT_MAPPER.createObjectNode();
      event.put("schemaVersion", 1);
      event.put("eventType", "REFUND_SUCCEEDED");
      event.put("orderType", "PAYIN");
      event.put("eventId", "refund-event-1");
      event.put("refundId", "refund-1");
      event.put("orderId", "order-1");
      event.put("merchantId", "merchant-1");
      event.put("amount", amount);
      event.put("currency", currency);
      event.put("eventSignature", hmac(OBJECT_MAPPER.writeValueAsString(event)));
      return OBJECT_MAPPER.writeValueAsString(event);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private static String hmac(String payload) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }
}
