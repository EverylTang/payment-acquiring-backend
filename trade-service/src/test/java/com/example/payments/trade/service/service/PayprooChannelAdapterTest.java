package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayprooChannelAdapterTest {
  private final PayprooChannelAdapter adapter = new PayprooChannelAdapter(new ObjectMapper());

  @Test
  void signsAndVerifiesAsciiSortedNonEmptyFields() throws Exception {
    var keys = KeyPairGenerator.getInstance("RSA");
    keys.initialize(2048);
    var pair = keys.generateKeyPair();
    var runtime =
        new ChannelRuntimeContext(
            "payproo-test",
            "PAYPROO",
            "https://example.test/create",
            PayprooChannelAdapter.SIGNATURE_PROFILE,
            Map.of(),
            Map.of(
                "merchantSecretKey",
                "merchant-secret",
                "merchantPrivateKey",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                "platformPublicKey",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
    var values = new LinkedHashMap<String, Object>();
    values.put("z", "last");
    values.put("a", "first");
    values.put("empty", "");
    values.put("sign_type", "RSA");

    assertThat(PayprooChannelAdapter.canonical(values)).isEqualTo("a=first&z=last");
    values.put("sign", adapter.sign(values, runtime));

    adapter.verify(values, runtime);
  }

  @Test
  void buildsTaiwanJkoPayloadFromChannelJsonMapping() {
    var runtime =
        new ChannelRuntimeContext(
            "payproo-twd-v1",
            "PAYPROO",
            "https://example.test/twd/collect/apply",
            PayprooChannelAdapter.SIGNATURE_PROFILE,
            Map.of(
                "appId",
                "1054",
                "requestFields",
                "appId,orderId,name,phone,email,amount,payType,payModel,callBackUrl,subject,userId,subMerchantId",
                "requiredFields",
                "appId,orderId,name,phone,email,amount,payType,payModel,callBackUrl,subject",
                "methodMappings",
                Map.of(
                    "TWD_JKO",
                    Map.of(
                        "payType", "EWALLET",
                        "payModel", "JKOPAY",
                        "requiredFields", java.util.List.of("userId", "subMerchantId"))),
                "amountScale",
                "0",
                "integerAmount",
                "true"),
            Map.of());
    var request =
        new PaymentChannelAdapter.PaymentChannelRequest(
            "attempt-1",
            "order-1",
            "merchant-1",
            "TWD",
            "TWD_JKO",
            "300",
            runtime,
            null,
            "https://merchant.example/payment/return",
            "order subject",
            Map.of(
                "name", "user10001",
                "phone", "0912345678",
                "email", "user@example.com",
                "userId", "10001",
                "subMerchantId", "sub-1"));

    var payload = adapter.createPayload(request);

    assertThat(payload)
        .containsEntry("appId", 1054L)
        .containsEntry("payType", "EWALLET")
        .containsEntry("payModel", "JKOPAY")
        .containsEntry("userId", "10001")
        .containsEntry("subMerchantId", "sub-1");
  }

  @Test
  void rejectsAValidlySignedCallbackForAnotherMerchantOrder() throws Exception {
    var keys = KeyPairGenerator.getInstance("RSA");
    keys.initialize(2048);
    var pair = keys.generateKeyPair();
    var runtime =
        new ChannelRuntimeContext(
            "payproo-test",
            "PAYPROO",
            "https://example.test/create",
            PayprooChannelAdapter.SIGNATURE_PROFILE,
            Map.of("appId", "1054"),
            Map.of(
                "merchantSecretKey",
                "merchant-secret",
                "merchantPrivateKey",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                "platformPublicKey",
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
    var payload = new LinkedHashMap<String, Object>();
    payload.put("appId", "1054");
    payload.put("orderId", "another-order");
    payload.put("orderNo", "provider-order");
    payload.put("currency", "TWD");
    payload.put("amount", "300");
    payload.put("status", "2");
    payload.put("sign", adapter.sign(payload, runtime));

    assertThatThrownBy(
            () ->
                adapter.verifyCallback(
                    new PaymentChannelAdapter.PaymentCallbackRequest(
                        new ObjectMapper().writeValueAsString(payload),
                        "embedded",
                        "callback-1",
                        "expected-order",
                        "TWD",
                        "300",
                        runtime)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("订单号不匹配");
  }
}
