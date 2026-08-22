package com.example.payments.fund.service.service;

import lombok.RequiredArgsConstructor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.payments.fund.service.mapper.RefundEventConsumptionMapper;
import com.example.payments.fund.service.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
    topic = "${fund.refund-success.topic:REFUND_SUCCEEDED}",
    consumerGroup = "${fund.refund-success.consumer-group:fund-refund-success}",
    maxReconsumeTimes = 5)
@RequiredArgsConstructor
public class RefundSuccessEventConsumer implements RocketMQListener<String> {
  private final LedgerEntryApplicationService ledger;
  private final ObjectMapper mapper;
  private final RefundEventConsumptionMapper consumption;
  private final MeterRegistry metrics;

  @Override
  public void onMessage(String message) {
    try {
      JsonNode e = mapper.readTree(message);
      String eventId = e.path("eventId").asText();
      String refundId = e.path("refundId").asText();
      String hash = sha256(message);
      var existing =
          consumption.selectOne(
              new LambdaQueryWrapper<RefundEventConsumptionEntity>()
                  .eq(RefundEventConsumptionEntity::getEventId, eventId));
      if (existing != null) {
        if (!hash.equals(existing.getPayloadHash()) || !refundId.equals(existing.getRefundId()))
          throw new IllegalStateException("refund event conflicts");
        if ("PROCESSED".equals(existing.getStatus())) return;
      }
      if (existing == null) {
        var record = new RefundEventConsumptionEntity();
        record.setEventId(eventId);
        record.setRefundId(refundId);
        record.setPayloadHash(hash);
        record.setStatus("PROCESSING");
        record.setConsumeCount(1);
        record.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        try {
          consumption.insert(record);
        } catch (DuplicateKeyException duplicate) {
          return;
        }
      }
      ledger.recordRefundReversal(
          e.get("refundId").asText(),
          e.get("orderId").asText(),
          e.get("merchantId").asText(),
          new BigDecimal(e.get("amount").asText()),
          e.get("currency").asText());
      var processed =
          consumption.selectOne(
              new LambdaQueryWrapper<RefundEventConsumptionEntity>()
                  .eq(RefundEventConsumptionEntity::getEventId, eventId));
      processed.setStatus("PROCESSED");
      processed.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
      processed.setLastError(null);
      consumption.updateById(processed);
    } catch (Exception ex) {
      metrics.counter("fund.refund.reversal.failed").increment();
      throw new IllegalStateException("refund reversal event failed", ex);
    }
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
