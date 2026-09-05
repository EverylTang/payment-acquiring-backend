package com.example.payments.trade.service.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MerchantNotificationHttpClient {
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public MerchantNotificationResponse post(
      URI uri, String eventId, String body, MerchantNotificationSignatureClient.Signature signature)
      throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("X-Payment-Notification-Id", eventId)
            .header("X-Payment-Key-Id", signature.keyId())
            .header("X-Payment-Timestamp", String.valueOf(signature.timestamp()))
            .header("X-Payment-Nonce", signature.nonce())
            .header("X-Payment-Signature", signature.signature())
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return new MerchantNotificationResponse(response.statusCode(), response.body());
  }
}
