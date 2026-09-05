package com.example.payments.platform.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.payments.platform.service.mapper.MerchantCredentialFullMapper;
import com.example.payments.platform.service.model.MerchantApiCredentialModel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MerchantApiAuthenticationServiceTest {
  private static final String SECRET = "merchant-test-secret";

  @Test
  void acceptsValidSignatureAndClaimsNonce() throws Exception {
    var mapper = Mockito.mock(MerchantCredentialFullMapper.class);
    var cipher = cipher();
    when(mapper.selectActiveApiCredential(any(), any()))
        .thenReturn(new MerchantApiCredentialModel("key-1", "merchant-1", cipher.encrypt(SECRET), "[\"203.0.113.8\"]", null));
    when(mapper.claimNonce(any(), any(), any(), any(), any())).thenReturn(1);
    var service = new MerchantApiAuthenticationService(mapper, cipher);
    String payload = "POST\n/api/v1/payments/orders\n\n1\nnonce-1\nhash";
    var request = new MerchantApiAuthenticationService.AuthenticationRequest("key-1", Instant.now().getEpochSecond(), "nonce-1", sign(payload), "203.0.113.8", payload);

    assertThat(service.authenticate(request)).isEqualTo("merchant-1");
  }

  @Test
  void rejectsReplayedNonceAndUnlistedSource() throws Exception {
    var mapper = Mockito.mock(MerchantCredentialFullMapper.class);
    var cipher = cipher();
    when(mapper.selectActiveApiCredential(any(), any()))
        .thenReturn(new MerchantApiCredentialModel("key-1", "merchant-1", cipher.encrypt(SECRET), "[\"203.0.113.8\"]", null));
    when(mapper.claimNonce(any(), any(), any(), any(), any())).thenReturn(0);
    var service = new MerchantApiAuthenticationService(mapper, cipher);
    String payload = "GET\n/api/v1/payments/orders/order-1\n\n1\nnonce-1\nhash";
    var replay = new MerchantApiAuthenticationService.AuthenticationRequest("key-1", Instant.now().getEpochSecond(), "nonce-1", sign(payload), "203.0.113.8", payload);
    var unlisted = new MerchantApiAuthenticationService.AuthenticationRequest("key-1", Instant.now().getEpochSecond(), "nonce-1", sign(payload), "203.0.113.9", payload);

    assertThatThrownBy(() -> service.authenticate(replay)).hasMessage("merchant request replayed");
    assertThatThrownBy(() -> service.authenticate(unlisted)).hasMessage("merchant authentication failed");
  }

  private static MerchantCredentialCipher cipher() {
    var cipher = new MerchantCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    cipher.initialize();
    return cipher;
  }

  private static String sign(String payload) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }
}
