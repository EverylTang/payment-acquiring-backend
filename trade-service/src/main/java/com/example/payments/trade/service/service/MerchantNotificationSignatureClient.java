package com.example.payments.trade.service.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class MerchantNotificationSignatureClient {
  private final RestClient client;
  private final String internalToken;

  public MerchantNotificationSignatureClient(
      @Value("${trade.routing.platform-base-url:http://127.0.0.1:8081}") String platformBaseUrl,
      @Value("${trade.routing.internal-token:${GATEWAY_INTERNAL_TOKEN:}}") String internalToken) {
    client = RestClient.builder().baseUrl(platformBaseUrl).build();
    this.internalToken = internalToken;
  }

  public Signature sign(String merchantId, String body) {
    Instant now = Instant.now();
    String nonce = UUID.randomUUID().toString();
    var result = client.post().uri("/api/internal/v1/merchant-authentication/notifications/sign")
        .headers(headers -> headers.set("X-Internal-Token", internalToken))
        .body(new SigningRequest(merchantId, body, nonce, now.getEpochSecond()))
        .retrieve().body(Signature.class);
    if (result == null || result.keyId() == null || result.signature() == null) throw new IllegalStateException("merchant notification signature is unavailable");
    return result;
  }

  private record SigningRequest(String merchantId, String body, String nonce, long timestamp) {}
  public record Signature(String keyId, long timestamp, String nonce, String signature) {}
}
