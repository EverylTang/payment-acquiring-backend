package com.example.payments.fund.service.service;

import com.example.payments.fund.service.mapper.PaymentEventConsumptionMapper;
import com.example.payments.fund.service.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
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
public class PaymentSuccessEventConsumer implements RocketMQListener<String> {
  private static final String EVENT_TYPE = "PAYMENT_SUCCEEDED";
  private static final long PROCESSING_LEASE_SECONDS = 60;
  private final LedgerEntryApplicationService ledgerService;
  private final MerchantSettlementService settlementService;
  private final PaymentEventConsumptionMapper consumptionMapper;
  private final ObjectMapper objectMapper;
  private final String signingSecret;
  private final String consumerId = UUID.randomUUID().toString();

  public PaymentSuccessEventConsumer(
      LedgerEntryApplicationService ledgerService,
      MerchantSettlementService settlementService,
      PaymentEventConsumptionMapper consumptionMapper,
      ObjectMapper objectMapper,
      @Value("${fund.payment-success.signing-secret:}") String signingSecret) {
    if (signingSecret == null || signingSecret.isBlank()) {
      throw new IllegalStateException("PAYMENT_SUCCESS_EVENT_SIGNING_SECRET must be configured");
    }
    this.ledgerService = ledgerService;
    this.settlementService = settlementService;
    this.consumptionMapper = consumptionMapper;
    this.objectMapper = objectMapper;
    this.signingSecret = signingSecret;
  }

  @Override
  public void onMessage(String message) {
    ObjectNode event = parseWithConfiguredMapper(message);
    int schemaVersion = event.path("schemaVersion").asInt(0);
    if (schemaVersion != 1)
      throw new IllegalArgumentException("unsupported payment event schema version");
    if (!EVENT_TYPE.equals(required(event, "eventType"))) {
      throw new IllegalArgumentException("unsupported payment event type");
    }
    if (!"PAYIN".equals(required(event, "orderType"))) {
      throw new IllegalArgumentException("payment success event must be PAYIN");
    }
    verifyEventSignature(event);
    String eventId = required(event, "eventId");
    String orderId = required(event, "orderId");
    String merchantId = required(event, "merchantId");
    String productCode = optional(event, "productCode");
    validateProductCode(productCode);
    String currency = required(event, "currency");
    validateCurrency(currency);
    BigDecimal amount = decimal(event, "amount");
    BigDecimal feeAmount = decimalOrZero(event, "feeAmount");
    validateAmounts(amount, feeAmount);
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
          idempotencyKey(orderId), orderId, merchantId, amount, feeAmount, currency);
      recordSettlementDetail(orderId, merchantId, productCode, amount, feeAmount, currency);
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
              idempotencyKey(orderId), orderId, merchantId, amount, feeAmount, currency);
      recordSettlementDetail(orderId, merchantId, productCode, amount, feeAmount, currency);
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
      throw new LedgerEntryApplicationService.LedgerConflictException(
          "event consumption conflicts");
    }
  }

  private ObjectNode parseWithConfiguredMapper(String message) {
    try {
      JsonNode event = objectMapper.readTree(message);
      if (!(event instanceof ObjectNode object)) {
        throw new IllegalArgumentException("payment success event must be an object");
      }
      return object;
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid payment success event", exception);
    }
  }

  private void verifyEventSignature(ObjectNode event) {
    String supplied = required(event, "eventSignature");
    ObjectNode unsigned = event.deepCopy();
    unsigned.remove("eventSignature");
    normalizeNumbers(unsigned);
    try {
      String expected = hmac(objectMapper.writeValueAsString(unsigned));
      if (!MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII),
          supplied.getBytes(StandardCharsets.US_ASCII))) {
        throw new IllegalArgumentException("invalid payment success event signature");
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalArgumentException("invalid payment success event", exception);
    }
  }

  private static void normalizeNumbers(JsonNode node) {
    if (node instanceof ObjectNode object) {
      var fields = new ArrayList<String>();
      object.fieldNames().forEachRemaining(fields::add);
      for (String field : fields) {
        var value = object.get(field);
        if (value.isNumber()) {
          object.set(field, DecimalNode.valueOf(canonicalDecimal(value.decimalValue())));
        } else {
          normalizeNumbers(value);
        }
      }
    } else if (node instanceof com.fasterxml.jackson.databind.node.ArrayNode array) {
      for (int index = 0; index < array.size(); index++) {
        var value = array.get(index);
        if (value.isNumber()) {
          array.set(index, DecimalNode.valueOf(canonicalDecimal(value.decimalValue())));
        } else {
          normalizeNumbers(value);
        }
      }
    }
  }

  private static BigDecimal canonicalDecimal(BigDecimal value) {
    var normalized = value.stripTrailingZeros();
    return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
  }

  private String hmac(String payload) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException(
          "payment success event signature verification failed", exception);
    }
  }

  private static String required(JsonNode event, String field) {
    JsonNode value = event.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank())
      throw new IllegalArgumentException("missing " + field);
    return value.textValue();
  }

  private static String optional(JsonNode event, String field) {
    JsonNode value = event.get(field);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException("invalid " + field);
    }
    return value.textValue();
  }

  private static void validateProductCode(String productCode) {
    if (productCode != null && !productCode.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
      throw new IllegalArgumentException("invalid productCode");
    }
  }

  private static String idempotencyKey(String orderId) {
    return "payment-success:" + orderId;
  }

  private void recordSettlementDetail(
      String orderId,
      String merchantId,
      String productCode,
      BigDecimal amount,
      BigDecimal feeAmount,
      String currency) {
    if (productCode == null) {
      settlementService.createSettlementDetail(orderId, merchantId, amount, feeAmount, currency);
      return;
    }
    settlementService.createSettlementDetail(
        orderId, merchantId, productCode, amount, feeAmount, currency);
  }

  private static BigDecimal decimalOrZero(JsonNode event, String field) {
    JsonNode value = event.get(field);
    if (value == null || value.isNull()) return BigDecimal.ZERO;
    if (!value.isNumber()) throw new IllegalArgumentException("invalid " + field);
    return value.decimalValue();
  }

  private static BigDecimal decimal(JsonNode event, String field) {
    JsonNode value = event.get(field);
    if (value == null || !value.isNumber()) throw new IllegalArgumentException("invalid " + field);
    return value.decimalValue();
  }

  private static void validateAmounts(BigDecimal amount, BigDecimal feeAmount) {
    if (amount.signum() <= 0
        || amount.scale() > 4
        || feeAmount.signum() < 0
        || feeAmount.scale() > 4
        || feeAmount.compareTo(amount) > 0) {
      throw new IllegalArgumentException("invalid payment amount or fee amount");
    }
  }

  private static void validateCurrency(String currency) {
    if (!currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("invalid currency");
    }
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
