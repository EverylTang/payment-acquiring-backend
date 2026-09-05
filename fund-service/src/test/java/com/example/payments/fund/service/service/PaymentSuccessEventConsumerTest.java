package com.example.payments.fund.service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.fund.service.mapper.LedgerEntryMapper;
import com.example.payments.fund.service.mapper.PaymentEventConsumptionMapper;
import com.example.payments.fund.service.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PaymentSuccessEventConsumerTest {
  private static final String SIGNING_SECRET = "test-payment-success-event-secret";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final LedgerEntryMapper ledgerMapper = org.mockito.Mockito.mock(LedgerEntryMapper.class);
  private final LedgerEntryApplicationService ledgerService =
      new LedgerEntryApplicationService(ledgerMapper);
  private final PaymentEventConsumptionMapper consumptionMapper =
      org.mockito.Mockito.mock(PaymentEventConsumptionMapper.class);
  private final PaymentSuccessEventConsumer consumer =
      new PaymentSuccessEventConsumer(ledgerService, consumptionMapper, OBJECT_MAPPER, SIGNING_SECRET);

  @Test
  void recordsPaymentSuccessOnce() {
    consumer.onMessage(event("PAYIN"));
    verify(ledgerMapper).insert(any(LedgerEntryEntity.class));
    verify(consumptionMapper).insert(any(PaymentEventConsumptionEntity.class));
  }

  @Test
  void conflictingExistingEventIsRejected() {
    var existing = new PaymentEventConsumptionEntity();
    existing.setPayloadHash("different");
    when(consumptionMapper.findByEvent("event-1", "PAYMENT_SUCCEEDED")).thenReturn(existing);
    assertThatThrownBy(
            () ->
                consumer.onMessage(event("PAYIN")))
        .isInstanceOf(LedgerEntryApplicationService.LedgerConflictException.class);
  }

  @Test
  void unsupportedSchemaVersionIsRejected() {
    assertThatThrownBy(() -> consumer.onMessage("{\"schemaVersion\":2,\"eventId\":\"event-1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("unsupported payment event schema version");
  }

  @Test
  void invalidEventIsRejected() {
    assertThatThrownBy(() -> consumer.onMessage("{\"eventId\":\"event-1\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void payoutEventIsRejectedBeforeFundsAreRecorded() {
    assertThatThrownBy(
            () ->
                consumer.onMessage(event("PAYOUT")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("payment success event must be PAYIN");
  }

  @Test
  void tamperedEventIsRejectedBeforeFundsAreRecorded() {
    assertThatThrownBy(() -> consumer.onMessage(event("PAYIN").replace("merchant-1", "merchant-2")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("invalid payment success event signature");
  }

  @Test
  void recordsTheFeeAsASeparateDebitEntry() {
    consumer.onMessage(event("PAYIN", new BigDecimal("0.25")));

    var entries =
        org.mockito.ArgumentCaptor.forClass(LedgerEntryEntity.class);
    verify(ledgerMapper, times(2)).insert(entries.capture());
    assertThat(entries.getAllValues())
        .anySatisfy(
            entry -> {
              org.assertj.core.api.Assertions.assertThat(entry.getEntryType())
                  .isEqualTo("PAYMENT_FEE");
              org.assertj.core.api.Assertions.assertThat(entry.getDebitCredit()).isEqualTo("DEBIT");
              org.assertj.core.api.Assertions.assertThat(entry.getAmount())
                  .isEqualByComparingTo("0.25");
            });
  }

  private static String event(String orderType) {
    return event(orderType, BigDecimal.ZERO);
  }

  private static String event(String orderType, BigDecimal feeAmount) {
    try {
      ObjectNode event = OBJECT_MAPPER.createObjectNode();
      event.put("schemaVersion", 1);
      event.put("eventType", "PAYMENT_SUCCEEDED");
      event.put("orderType", orderType);
      event.put("eventId", "event-1");
      event.put("orderId", "order-1");
      event.put("merchantId", "merchant-1");
      event.put("amount", new BigDecimal("10.25"));
      event.put("feeAmount", feeAmount);
      event.put("currency", "USD");
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
