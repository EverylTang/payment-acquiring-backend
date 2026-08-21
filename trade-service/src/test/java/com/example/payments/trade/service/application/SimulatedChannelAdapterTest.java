package com.example.payments.trade.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SimulatedChannelAdapterTest {
  private final SimulatedChannelAdapter adapter = new SimulatedChannelAdapter("test-secret");

  @Test
  void createsConfiguredStatuses() {
    var result = adapter.createPayment(new PaymentChannelAdapter.PaymentChannelRequest("a1", "o1", "m1", "USD", "CARD", "10.00", "TIMEOUT"));
    assertThat(result.status()).isEqualTo("TIMEOUT");
    assertThat(result.channelOrderId()).isEqualTo("sim-a1");
  }

  @Test
  void verifiesSignedCallbackAndRejectsTampering() {
    var payload = "sim-a1|SUCCESS|1700000000";
    var signature = adapter.sign(payload);
    assertThat(adapter.verifyCallback(new PaymentChannelAdapter.PaymentCallbackRequest(payload, signature, "cb-1")).status()).isEqualTo("SUCCESS");
    assertThatThrownBy(() -> adapter.verifyCallback(new PaymentChannelAdapter.PaymentCallbackRequest(payload, "bad", "cb-2")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
