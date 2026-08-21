package com.example.payments.fund.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.payments.fund.service.infrastructure.persistence.LedgerEntryEntity;
import com.example.payments.fund.service.infrastructure.persistence.LedgerEntryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class PaymentSuccessEventConsumerTest {
  private final LedgerEntryMapper mapper = org.mockito.Mockito.mock(LedgerEntryMapper.class);
  private final PaymentSuccessEventConsumer consumer = new PaymentSuccessEventConsumer(mapper, new ObjectMapper());

  @Test
  void recordsPaymentSuccessOnce() {
    consumer.onMessage("{\"eventId\":\"event-1\",\"orderId\":\"order-1\",\"merchantId\":\"merchant-1\",\"amount\":10.25,\"currency\":\"USD\"}");

    var captor = ArgumentCaptor.forClass(LedgerEntryEntity.class);
    verify(mapper).insert(captor.capture());
    var entry = captor.getValue();
    assertThat(entry.getIdempotencyKey()).isEqualTo("payment-success:order-1");
    assertThat(entry.getAmount()).isEqualByComparingTo("10.25");
  }

  @Test
  void duplicateEntryIsAcknowledged() {
    doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(LedgerEntryEntity.class));
    consumer.onMessage("{\"eventId\":\"event-1\",\"orderId\":\"order-1\",\"merchantId\":\"merchant-1\",\"amount\":10.25,\"currency\":\"USD\"}");
  }

  @Test
  void invalidEventIsRejected() {
    assertThatThrownBy(() -> consumer.onMessage("{\"eventId\":\"event-1\"}"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
