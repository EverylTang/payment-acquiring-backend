package com.example.payments.fund.service.service;

import lombok.RequiredArgsConstructor;

import com.example.payments.fund.service.mapper.PaymentEventConsumptionMapper;
import com.example.payments.fund.service.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
    topic = "${fund.payment-success.topic:PAYMENT_SUCCEEDED}",
    consumerGroup = "${fund.payment-success.consumer-group:fund-payment-success}",
    maxReconsumeTimes = 5,
    consumeMode = ConsumeMode.CONCURRENTLY,
    messageModel = MessageModel.CLUSTERING,
    consumeTimeout = 60)
@RequiredArgsConstructor
public class PaymentSuccessEventConsumer implements RocketMQListener<String> {
  private static final String EVENT_TYPE = "PAYMENT_SUCCEEDED";
  private static final long PROCESSING_LEASE_SECONDS = 60;
  private final LedgerEntryApplicationService ledgerService;
  private final PaymentEventConsumptionMapper consumptionMapper;
  private final ObjectMapper objectMapper;
  private final String consumerId = UUID.randomUUID().toString();

  @Override
  public void onMessage(String message) {
    JsonNode event = parseWithConfiguredMapper(message);
    int schemaVersion = event.path("schemaVersion").asInt(0);
    if (schemaVersion != 1)
      throw new IllegalArgumentException("unsupported payment event schema version");
    String eventId = required(event, "eventId");
    String orderId = required(event, "orderId");
    String merchantId = required(event, "merchantId");
    String currency = required(event, "currency");
    BigDecimal amount = event.required("amount").decimalValue();
    String hash = sha256(message);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    PaymentEventConsumptionEntity record = consumptionMapper.findByEvent(eventId, EVENT_TYPE);
    boolean claimedOnInsert = false;
    if (record == null) {
      record =
          createRecord(event, eventId, orderId, merchantId, currency, amount, message, hash, now);
      try {
        consumptionMapper.insert(record);
        claimedOnInsert = true;
      } catch (DuplicateKeyException duplicate) {
        record = consumptionMapper.findByEvent(eventId, EVENT_TYPE);
        if (record == null) throw duplicate;
      }
    }
    validateExisting(record, eventId, orderId, merchantId, currency, amount, hash);
    if ("PROCESSED".equals(record.getStatus()) || "DUPLICATE".equals(record.getStatus())) {
      ledgerService.recordPaymentSuccess(
          idempotencyKey(orderId), orderId, merchantId, amount, currency);
      return;
    }
    if ("FAILED".equals(record.getStatus()) || "REPLAYING".equals(record.getStatus())) {
      if (consumptionMapper.claim(
              record.getId(), consumerId, now, now.plusSeconds(PROCESSING_LEASE_SECONDS))
          != 1) {
        throw new IllegalStateException("payment event consumption claim lost");
      }
      record.setStatus("PROCESSING");
    } else if ("PROCESSING".equals(record.getStatus())) {
      if (claimedOnInsert) {
        record.setProcessingOwner(consumerId);
      } else {
        if (record.getProcessingUntil() != null && record.getProcessingUntil().isAfter(now)) {
          throw new IllegalStateException("payment event is already processing");
        }
        if (consumptionMapper.claim(
                record.getId(), consumerId, now, now.plusSeconds(PROCESSING_LEASE_SECONDS))
            != 1) {
          throw new IllegalStateException("payment event consumption claim lost");
        }
      }
    } else {
      throw new IllegalStateException("unsupported payment event status: " + record.getStatus());
    }
    try {
      var result =
          ledgerService.recordPaymentSuccess(
              idempotencyKey(orderId), orderId, merchantId, amount, currency);
      record.setStatus(result.duplicate() ? "DUPLICATE" : "PROCESSED");
      record.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
      record.setLastError(null);
      record.setLedgerEntryId(result.entry().getEntryId());
      record.setProcessingOwner(null);
      record.setProcessingUntil(null);
      consumptionMapper.updateById(record);
    } catch (RuntimeException exception) {
      String failureType =
          exception instanceof LedgerEntryApplicationService.LedgerConflictException
              ? "CONFLICT"
              : "PROCESSING";
      consumptionMapper.markFailed(
          record.getId(),
          consumerId,
          failureType,
          truncate(exception.getMessage()),
          LocalDateTime.now(ZoneOffset.UTC));
      throw exception;
    }
  }

  private PaymentEventConsumptionEntity createRecord(
      JsonNode event,
      String eventId,
      String orderId,
      String merchantId,
      String currency,
      BigDecimal amount,
      String message,
      String hash,
      LocalDateTime now) {
    var record = new PaymentEventConsumptionEntity();
    record.setEventId(eventId);
    record.setEventType(EVENT_TYPE);
    record.setOrderId(orderId);
    record.setAttemptId(event.path("attemptId").textValue());
    record.setMerchantId(merchantId);
    record.setAmount(amount);
    record.setCurrency(currency);
    record.setPayload(message);
    record.setPayloadHash(hash);
    record.setStatus("PROCESSING");
    record.setConsumeCount(1);
    record.setFirstReceivedAt(now);
    record.setLastReceivedAt(now);
    record.setProcessingOwner(consumerId);
    record.setProcessingUntil(now.plusSeconds(PROCESSING_LEASE_SECONDS));
    return record;
  }

  private static void validateExisting(
      PaymentEventConsumptionEntity record,
      String eventId,
      String orderId,
      String merchantId,
      String currency,
      BigDecimal amount,
      String hash) {
    if (!hash.equals(record.getPayloadHash())
        || !eventId.equals(record.getEventId())
        || !orderId.equals(record.getOrderId())
        || !merchantId.equals(record.getMerchantId())
        || !currency.equals(record.getCurrency())
        || amount.compareTo(record.getAmount()) != 0) {
      throw new LedgerEntryApplicationService.LedgerConflictException( "event consumption conflicts");
    }
  }

  private JsonNode parseWithConfiguredMapper(String message) {
    try {
      return objectMapper.readTree(message);
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid payment success event", exception);
    }
  }

  private static String required(JsonNode event, String field) {
    JsonNode value = event.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank())
      throw new IllegalArgumentException("missing " + field);
    return value.textValue();
  }

  private static String idempotencyKey(String orderId) {
    return "payment-success:" + orderId;
  }

  private static String truncate(String value) {
    if (value == null) return "payment success event processing failed";
    return value.length() <= 512 ? value : value.substring(0, 512);
  }

  private static String sha256(String payload) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
