package com.example.payments.trade.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Signs the stable pay-in success event before it leaves Trade Service. */
@Component
public class PaymentSuccessEventSigner {
  private final ObjectMapper objectMapper;
  private final String signingSecret;

  public PaymentSuccessEventSigner(
      ObjectMapper objectMapper,
      @Value("${trade.payment-success-event.signing-secret:}") String signingSecret) {
    this.objectMapper = objectMapper;
    this.signingSecret = signingSecret;
  }

  @PostConstruct
  void requireSigningSecret() {
    if (signingSecret.isBlank()) {
      throw new IllegalStateException("PAYMENT_SUCCESS_EVENT_SIGNING_SECRET must be configured");
    }
  }

  public String signedPayload(Object event) {
    ObjectNode unsigned = objectMapper.valueToTree(event);
    normalizeNumbers(unsigned);
    try {
      unsigned.put("eventSignature", hmac(objectMapper.writeValueAsString(unsigned)));
      return objectMapper.writeValueAsString(unsigned);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("payment success event serialization failed", exception);
    }
  }

  private static void normalizeNumbers(com.fasterxml.jackson.databind.JsonNode node) {
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
      throw new IllegalStateException("payment success event signing failed", exception);
    }
  }
}
