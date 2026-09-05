package com.example.payments.fund.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.payments.fund.service.mapper.RefundEventConsumptionMapper;
import com.example.payments.fund.service.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
    topic = "${fund.refund-success.topic:REFUND_SUCCEEDED}",
    consumerGroup = "${fund.refund-success.consumer-group:fund-refund-success}",
    maxReconsumeTimes = 5)
public class RefundSuccessEventConsumer implements RocketMQListener<String> {
  private static final String EVENT_TYPE = "REFUND_SUCCEEDED";
  private final LedgerEntryApplicationService ledger;
  private final ObjectMapper mapper;
  private final RefundEventConsumptionMapper consumption;
  private final MeterRegistry metrics;
  private final String signingSecret;

  public RefundSuccessEventConsumer(
      LedgerEntryApplicationService ledger,
      ObjectMapper mapper,
      RefundEventConsumptionMapper consumption,
      MeterRegistry metrics,
      @Value("${fund.refund-success.signing-secret:}") String signingSecret) {
    if (signingSecret == null || signingSecret.isBlank()) {
      throw new IllegalStateException("REFUND_SUCCESS_EVENT_SIGNING_SECRET must be configured");
    }
    this.ledger = ledger;
    this.mapper = mapper;
    this.consumption = consumption;
    this.metrics = metrics;
    this.signingSecret = signingSecret;
  }

  @Override
  public void onMessage(String message) {
    try {
      ObjectNode e = parse(message);
      if (e.path("schemaVersion").asInt(0) != 1) {
        throw new IllegalArgumentException("unsupported refund event schema version");
      }
      if (!EVENT_TYPE.equals(required(e, "eventType"))) {
        throw new IllegalArgumentException("unsupported refund event type");
      }
      if (!"PAYIN".equals(required(e, "orderType"))) {
        throw new IllegalArgumentException("refund success event must be PAYIN");
      }
      verifyEventSignature(e);
      String eventId = required(e, "eventId");
      String refundId = required(e, "refundId");
      String orderId = required(e, "orderId");
      String merchantId = required(e, "merchantId");
      String currency = required(e, "currency");
      BigDecimal amount = decimal(e, "amount");
      String hash = sha256(message);
      RefundEventConsumptionEntity record =
          consumption.selectOne(
              new LambdaQueryWrapper<RefundEventConsumptionEntity>()
                  .eq(RefundEventConsumptionEntity::getEventId, eventId));
      if (record != null) {
        if (!hash.equals(record.getPayloadHash()) || !refundId.equals(record.getRefundId()))
          throw new IllegalStateException("refund event conflicts");
        if ("PROCESSED".equals(record.getStatus())) return;
      }
      if (record == null) {
        record = new RefundEventConsumptionEntity();
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
      ledger.recordRefundReversal(refundId, orderId, merchantId, amount, currency);
      record.setStatus("PROCESSED");
      record.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
      record.setLastError(null);
      consumption.updateById(record);
    } catch (IllegalArgumentException exception) {
      metrics.counter("fund.refund.reversal.failed").increment();
      throw exception;
    } catch (Exception ex) {
      metrics.counter("fund.refund.reversal.failed").increment();
      throw new IllegalStateException("refund reversal event failed", ex);
    }
  }

  private ObjectNode parse(String message) {
    try {
      JsonNode event = mapper.readTree(message);
      if (!(event instanceof ObjectNode object)) {
        throw new IllegalArgumentException("refund success event must be an object");
      }
      return object;
    } catch (Exception exception) {
      throw new IllegalArgumentException("invalid refund success event", exception);
    }
  }

  private void verifyEventSignature(ObjectNode event) {
    String supplied = required(event, "eventSignature");
    ObjectNode unsigned = event.deepCopy();
    unsigned.remove("eventSignature");
    normalizeNumbers(unsigned);
    try {
      String expected = hmac(mapper.writeValueAsString(unsigned));
      if (!MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII),
          supplied.getBytes(StandardCharsets.US_ASCII))) {
        throw new IllegalArgumentException("invalid refund success event signature");
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalArgumentException("invalid refund success event", exception);
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
          "refund success event signature verification failed", exception);
    }
  }

  private static String required(JsonNode event, String field) {
    JsonNode value = event.get(field);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException("missing " + field);
    }
    return value.textValue();
  }

  private static BigDecimal decimal(JsonNode event, String field) {
    JsonNode value = event.get(field);
    if (value == null || !value.isNumber()) {
      throw new IllegalArgumentException("missing or invalid " + field);
    }
    return value.decimalValue();
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
