package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.MerchantCredentialFullMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantApiAuthenticationService {
  private static final long MAX_CLOCK_SKEW_SECONDS = 300;
  private final MerchantCredentialFullMapper credentialMapper;
  private final MerchantCredentialCipher cipher;

  public String authenticate(AuthenticationRequest request) {
    Instant now = Instant.now();
    if (Math.abs(now.getEpochSecond() - request.timestamp()) > MAX_CLOCK_SKEW_SECONDS) {
      throw new IllegalArgumentException(
          "merchant request timestamp is outside the permitted window");
    }
    var credential = credentialMapper.selectActiveApiCredential(request.keyId(), now);
    if (credential == null || !allowedSource(request.sourceIp(), credential.ipAllowlist())) {
      throw new IllegalArgumentException("merchant authentication failed");
    }
    String expected =
        hmac(cipher.decrypt(credential.secretCiphertext()), request.canonicalPayload());
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        request.signature().getBytes(StandardCharsets.US_ASCII))) {
      throw new IllegalArgumentException("merchant authentication failed");
    }
    if (credentialMapper.claimNonce(
            credential.merchantId(),
            request.keyId(),
            request.nonce(),
            now.plusSeconds(MAX_CLOCK_SKEW_SECONDS),
            now)
        != 1) {
      throw new IllegalArgumentException("merchant request replayed");
    }
    return credential.merchantId();
  }

  private static boolean allowedSource(String sourceIp, String allowlist) {
    if (allowlist == null || allowlist.isBlank() || "[]".equals(allowlist)) return true;
    return java.util.Arrays.stream(
            allowlist.replace("[", "").replace("]", "").replace("\"", "").split(","))
        .map(String::trim)
        .anyMatch(sourceIp::equals);
  }

  private static String hmac(String secret, String payload) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("merchant request signature verification failed", exception);
    }
  }

  public record AuthenticationRequest(
      String keyId,
      long timestamp,
      String nonce,
      String signature,
      String sourceIp,
      String canonicalPayload) {}
}
