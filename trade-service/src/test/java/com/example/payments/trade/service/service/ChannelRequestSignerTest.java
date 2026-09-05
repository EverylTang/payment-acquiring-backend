package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelRequestSignerTest {
  private final ChannelRequestSigner signer = new ChannelRequestSigner();

  @Test
  void createsStableMd5AndHmacSignaturesFromConfiguredFields() {
    var fields = Map.of("amount", "10.00", "orderId", "o1", "ignored", "value");

    var md5 = signer.sign(runtime("MD5_KEY_SUFFIX_V1", "test-secret"), fields);
    var hmac = signer.sign(runtime("HMAC_SHA256_V1", "test-secret"), fields);

    assertThat(md5.signedFields())
        .containsEntry("amount", "10.00")
        .containsEntry("orderId", "o1")
        .hasSize(2);
    assertThat(md5.value()).isEqualTo("1b3854ae1889bccbfdea4a10c8922e06");
    assertThat(hmac.value()).isEqualTo("c1ef7dde6722ac9c77b95bdba63eafebd24a421888383faa6339b785e7eaee17");
  }

  @Test
  void signsRsaUsingPkcs8Credential() throws Exception {
    var keys = KeyPairGenerator.getInstance("RSA");
    keys.initialize(2048);
    var pair = keys.generateKeyPair();
    var privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    var signature = signer.sign(runtime("RSA_SHA256_V1", privateKey), Map.of("orderId", "o1", "amount", "10.00"));

    var verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(pair.getPublic());
    verifier.update("amount=10.00&orderId=o1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertThat(verifier.verify(Base64.getDecoder().decode(signature.value()))).isTrue();
  }

  private ChannelRuntimeContext runtime(String profile, String secret) {
    return new ChannelRuntimeContext(
        "channel-1",
        "SIMULATED",
        "https://example.test/pay",
        profile,
        Map.of("signatureFields", "amount,orderId"),
        Map.of("requestSigningKey", secret),
        1);
  }
}
