package com.example.payments.fund.service.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
    topic = "${fund.refund-success.topic:REFUND_SUCCEEDED}",
    consumerGroup = "${fund.refund-success.consumer-group:fund-refund-success}",
    maxReconsumeTimes = 5)
public class RefundSuccessEventConsumer implements RocketMQListener<String> {
  private final LedgerEntryApplicationService ledger;
  private final ObjectMapper mapper;

  public RefundSuccessEventConsumer(LedgerEntryApplicationService ledger, ObjectMapper mapper) {
    this.ledger = ledger;
    this.mapper = mapper;
  }

  @Override
  public void onMessage(String message) {
    try {
      JsonNode e = mapper.readTree(message);
      ledger.recordRefundReversal(
          e.get("refundId").asText(),
          e.get("orderId").asText(),
          e.get("merchantId").asText(),
          new BigDecimal(e.get("amount").asText()),
          e.get("currency").asText());
    } catch (Exception ex) {
      throw new IllegalStateException("refund reversal event failed", ex);
    }
  }
}
