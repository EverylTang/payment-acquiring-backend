package com.example.payments.fund.service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.fund.service.model.*;
import com.example.payments.fund.service.mapper.LedgerEntryMapper;
import com.example.payments.fund.service.model.*;
import com.example.payments.fund.service.mapper.PaymentEventConsumptionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentSuccessEventConsumerTest {
  private final LedgerEntryMapper ledgerMapper = org.mockito.Mockito.mock(LedgerEntryMapper.class);
  private final LedgerEntryApplicationService ledgerService = new LedgerEntryApplicationService(ledgerMapper);
  private final PaymentEventConsumptionMapper consumptionMapper = org.mockito.Mockito.mock(PaymentEventConsumptionMapper.class);
  private final PaymentSuccessEventConsumer consumer = new PaymentSuccessEventConsumer(ledgerService, consumptionMapper, new ObjectMapper());

  @Test
  void recordsPaymentSuccessOnce() {
    consumer.onMessage("{\"schemaVersion\":1,\"eventId\":\"event-1\",\"orderId\":\"order-1\",\"merchantId\":\"merchant-1\",\"amount\":10.25,\"currency\":\"USD\"}");
    verify(ledgerMapper).insert(any(LedgerEntryEntity.class));
    verify(consumptionMapper).insert(any(PaymentEventConsumptionEntity.class));
  }

  @Test
  void conflictingExistingEventIsRejected() {
    var existing = new PaymentEventConsumptionEntity();
    existing.setPayloadHash("different");
    when(consumptionMapper.findByEvent("event-1", "PAYMENT_SUCCEEDED")).thenReturn(existing);
    assertThatThrownBy(() -> consumer.onMessage("{\"schemaVersion\":1,\"eventId\":\"event-1\",\"orderId\":\"order-1\",\"merchantId\":\"merchant-1\",\"amount\":10.25,\"currency\":\"USD\"}"))
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
}
