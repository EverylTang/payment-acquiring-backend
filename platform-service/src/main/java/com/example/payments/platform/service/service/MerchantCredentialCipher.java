package com.example.payments.platform.service.service;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MerchantCredentialCipher {
  private static final int NONCE_BYTES = 12;
  private final String encryptionKey;
  private SecretKeySpec key;

  public MerchantCredentialCipher(
      @Value("${platform.merchant-api.credential-encryption-key:}") String encryptionKey) {
    this.encryptionKey = encryptionKey;
  }

  @PostConstruct
  void initialize() {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(encryptionKey);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("merchant credential encryption key must be base64", exception);
    }
    if (decoded.length != 32) {
      throw new IllegalStateException("merchant credential encryption key must be 32 bytes");
    }
    key = new SecretKeySpec(decoded, "AES");
  }

  public String encrypt(String value) {
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      new SecureRandom().nextBytes(nonce);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("merchant credential encryption failed", exception);
    }
  }

  public String decrypt(String value) {
    try {
      byte[] payload = Base64.getDecoder().decode(value);
      if (payload.length <= NONCE_BYTES) throw new IllegalArgumentException("invalid credential ciphertext");
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, payload, 0, NONCE_BYTES));
      return new String(cipher.doFinal(payload, NONCE_BYTES, payload.length - NONCE_BYTES), StandardCharsets.UTF_8);
    } catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("merchant credential decryption failed", exception);
    }
  }
}
