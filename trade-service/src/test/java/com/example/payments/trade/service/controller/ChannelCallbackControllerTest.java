package com.example.payments.trade.service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.trade.service.service.ChannelAdapterRegistry;
import com.example.payments.trade.service.service.ChannelRuntimeContext;
import com.example.payments.trade.service.service.PaymentAttemptService;
import com.example.payments.trade.service.service.PaymentChannelAdapter;
import com.example.payments.trade.service.service.PlatformChannelConfigurationClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelCallbackControllerTest {
  @Test
  void forwardsTheProviderSignatureHeaderToTheChannelAdapterFlow() {
    var attempts = mock(PaymentAttemptService.class);
    var configurations = mock(PlatformChannelConfigurationClient.class);
    var adapters = mock(ChannelAdapterRegistry.class);
    var adapter = mock(PaymentChannelAdapter.class);
    var runtime =
        new ChannelRuntimeContext(
            "simulated", "SIMULATED", "https://example.test/pay", "SIMULATED_SHA256_PREFIX_V1", Map.of(), Map.of(), 1);
    when(configurations.resolve("simulated")).thenReturn(runtime);
    when(adapters.required("SIMULATED", "SIMULATED_SHA256_PREFIX_V1")).thenReturn(adapter);

    var response =
        new ChannelCallbackController(attempts, configurations, adapters)
            .callback("simulated", "sim-a1|SUCCESS|1700000000", "provider-signature");

    assertThat(response.getBody()).isEqualTo("SUCCESS");
    verify(attempts)
        .callback(
            "simulated",
            "sim-a1|SUCCESS|1700000000",
            "provider-signature",
            "626ebae0aef30e5fe8a6a51e65a3577c0c9f2115efbb760efc6c1cf9d558866f");
  }
}
