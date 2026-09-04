package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimulatedChannelAdapterTest {
  private final SimulatedChannelAdapter adapter = new SimulatedChannelAdapter("test-secret");

  @Test
  void createsConfiguredStatuses() {
    var result =
        adapter.createPayment(
            new PaymentChannelAdapter.PaymentChannelRequest(
                "a1", "o1", "m1", "USD", "CARD", "10.00", "TIMEOUT", null, null));
    assertThat(result.status()).isEqualTo("TIMEOUT");
    assertThat(result.channelOrderId()).isEqualTo("sim-a1");
  }

  @Test
  void verifiesSignedCallbackAndRejectsTampering() {
    var payload = "sim-a1|SUCCESS|1700000000";
    var signature = adapter.sign(payload);
    assertThat(adapter.callbackChannelOrderId(payload)).isEqualTo("sim-a1");
    assertThat(
            adapter
                .verifyCallback(
                    new PaymentChannelAdapter.PaymentCallbackRequest(payload, signature, "cb-1", null))
                .status())
        .isEqualTo("SUCCESS");
    assertThatThrownBy(
            () ->
                adapter.verifyCallback(
                    new PaymentChannelAdapter.PaymentCallbackRequest(payload, "bad", "cb-2", null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void verifiesRefundCallbackWithRuntimeCredential() throws Exception {
    var runtime =
        new ChannelRuntimeContext(
            "channel-1",
            "SIMULATED",
            "https://example.test/pay",
            "SIMULATED_SHA256_PREFIX_V1",
            Map.of(),
            Map.of("callbackVerifyKey", "runtime-secret"));
    var payload = "refund-1|SUCCESS";
    var timestamp = System.currentTimeMillis() / 1000;
    var signed = timestamp + ".nonce-1." + payload;
    var signature =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(("runtime-secret." + signed).getBytes(StandardCharsets.UTF_8)));

    assertThat(
            adapter
                .verifyRefundCallback(
                    new PaymentChannelAdapter.PaymentRefundCallbackRequest(
                        payload, signature, "callback-1", timestamp, "nonce-1", runtime))
                .status())
        .isEqualTo("SUCCESS");
  }
}
