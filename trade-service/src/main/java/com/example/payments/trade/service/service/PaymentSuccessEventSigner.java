package com.example.payments.trade.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
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
    try {
      unsigned.put("eventSignature", hmac(objectMapper.writeValueAsString(unsigned)));
      return objectMapper.writeValueAsString(unsigned);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("payment success event serialization failed", exception);
    }
  }

  private String hmac(String payload) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("payment success event signing failed", exception);
    }
  }
}
