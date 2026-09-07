package com.example.payments.platform.service.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Generates a complete merchant order request for local signature integration testing. */
class MerchantOrderSignatureGeneratorTest {
  private static final String TEST_KEY_ID = "merchant-test-key-1";
  private static final String TEST_SECRET = "merchant-test-secret";
  private static final String TEST_IDEMPOTENCY_KEY = "order-test-idempotency-1";

  @Test
  void generatesMerchantOrderSignatureAndHeaders() {
    GeneratedRequest request = generateDemo();

    assertThat(request.bodyHash()).matches("[0-9a-f]{64}");
    assertThat(request.canonicalPayload().lines()).hasSize(6);
    assertThat(request.signature()).isNotBlank();
    assertThat(request.headers())
        .containsEntry("X-Merchant-Key-Id", TEST_KEY_ID)
        .containsEntry("Idempotency-Key", TEST_IDEMPOTENCY_KEY)
        .containsKey("X-Merchant-Signature");

    print(request);
  }

  /** Run this method from an IDE to print a fresh request vector. */
  public static void main(String[] args) {
    print(generateDemo());
  }

  public static GeneratedRequest generateDemo() {
    String body =
        "{\"merchantOrderNo\":\"M-TEST-001\","
            + "\"appId\":\"1000\","
            + "\"payModel\":\"CARD\","
            + "\"country\":\"US\","
            + "\"currency\":\"USD\","
            + "\"amount\":100.00,"
            + "\"channelParams\":{}}";
    return generate(
        TEST_KEY_ID,
        TEST_SECRET,
        TEST_IDEMPOTENCY_KEY,
        "POST",
        "/api/v1/payments/orders",
        "",
        body,
        Instant.now().getEpochSecond(),
        UUID.randomUUID().toString());
  }

  public static GeneratedRequest generate(
      String keyId,
      String secret,
      String idempotencyKey,
      String method,
      String rawPath,
      String rawQuery,
      String rawBody,
      long timestamp,
      String nonce) {
    String normalizedQuery = normalizeQuery(rawQuery);
    String bodyHash = sha256(rawBody);
    String canonical =
        String.join(
            "\n",
            method,
            rawPath,
            normalizedQuery,
            Long.toString(timestamp),
            nonce,
            bodyHash);
    String signature = hmacSha256Base64Url(secret, canonical);

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("X-Merchant-Key-Id", keyId);
    headers.put("X-Merchant-Timestamp", Long.toString(timestamp));
    headers.put("X-Merchant-Nonce", nonce);
    headers.put("X-Merchant-Signature", signature);
    headers.put("Idempotency-Key", idempotencyKey);
    return new GeneratedRequest(
        method, rawPath, normalizedQuery, rawBody, bodyHash, timestamp, nonce, canonical, signature, headers);
  }

  private static String normalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) return "";
    return Stream.of(rawQuery.split("&", -1)).sorted().reduce((left, right) -> left + "&" + right).orElse("");
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String hmacSha256Base64Url(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
    }
  }

  private static void print(GeneratedRequest request) {
    System.out.println("=== merchant order request test vector ===");
    System.out.println("method: " + request.method());
    System.out.println("rawPath: " + request.rawPath());
    System.out.println("normalizedQuery: " + request.normalizedQuery());
    System.out.println("timestamp: " + request.timestamp());
    System.out.println("nonce: " + request.nonce());
    System.out.println("bodyHash: " + request.bodyHash());
    System.out.println("canonicalPayload:");
    System.out.println(request.canonicalPayload());
    System.out.println("signature: " + request.signature());
    System.out.println("headers:");
    request.headers().forEach((name, value) -> System.out.println(name + ": " + value));
    System.out.println("body: " + request.body());
  }

  public record GeneratedRequest(
      String method,
      String rawPath,
      String normalizedQuery,
      String body,
      String bodyHash,
      long timestamp,
      String nonce,
      String canonicalPayload,
      String signature,
      Map<String, String> headers) {}
}
