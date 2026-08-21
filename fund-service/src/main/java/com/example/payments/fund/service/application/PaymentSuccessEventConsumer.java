package com.example.payments.fund.service.application;

import com.example.payments.fund.service.infrastructure.persistence.LedgerEntryEntity;
import com.example.payments.fund.service.infrastructure.persistence.LedgerEntryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(topic = "PAYMENT_SUCCEEDED", consumerGroup = "fund-payment-success")
public class PaymentSuccessEventConsumer implements RocketMQListener<String> {
  private final LedgerEntryMapper mapper;
  private final ObjectMapper objectMapper;

  public PaymentSuccessEventConsumer(LedgerEntryMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public void onMessage(String message) {
    try {
      JsonNode event = objectMapper.readTree(message);
      String orderId = required(event, "orderId");
      String merchantId = required(event, "merchantId");
      String eventId = required(event, "eventId");
      var entry = new LedgerEntryEntity();
      entry.setEntryId("entry-" + orderId);
      entry.setAccountId(merchantId);
      entry.setOrderId(orderId);
      entry.setEntryType("PAYMENT_SUCCESS");
      entry.setDebitCredit("CREDIT");
      entry.setAmount(event.required("amount").decimalValue());
      entry.setCurrency(required(event, "currency"));
      entry.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
      entry.setIdempotencyKey("payment-success:" + orderId);
      entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
      mapper.insert(entry);
    } catch (DuplicateKeyException duplicate) {
      return;
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid payment success event", exception);
    }
  }

  private static String required(JsonNode event, String field) {
    return event.required(field).textValue();
  }
}
