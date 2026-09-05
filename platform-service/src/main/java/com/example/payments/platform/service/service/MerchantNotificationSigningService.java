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
public class MerchantNotificationSigningService {
  private final MerchantCredentialFullMapper credentialMapper;
  private final MerchantCredentialCipher cipher;

  public SignedNotification sign(String merchantId, String body, String nonce, Instant timestamp) {
    var credential =
        credentialMapper.selectActiveCredentialByMerchantAndType(merchantId, "WEBHOOK", timestamp);
    if (credential == null)
      throw new IllegalArgumentException("merchant webhook credential is unavailable");
    String canonical = timestamp.getEpochSecond() + "\n" + nonce + "\n" + sha256(body);
    return new SignedNotification(
        credential.credentialId(),
        timestamp.getEpochSecond(),
        nonce,
        hmac(cipher.decrypt(credential.secretCiphertext()), canonical));
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String hmac(String secret, String value) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("merchant notification signing failed", exception);
    }
  }

  public record SignedNotification(String keyId, long timestamp, String nonce, String signature) {}
}
