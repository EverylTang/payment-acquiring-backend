package com.example.payments.trade.service.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

class SimulatedChannelProductionGuardTest {
  @Test
  void allowsSimulatedSecretInDevProfile() {
    var environment = Mockito.mock(Environment.class);
    Mockito.when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});

    assertThatCode(
            () ->
                new SimulatedChannelProductionGuard(environment, "dev-secret")
                    .rejectSimulatedChannelConfiguration())
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsSimulatedSecretOutsideLocalDevAndTestProfiles() {
    var environment = Mockito.mock(Environment.class);
    Mockito.when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});

    assertThatThrownBy(
            () ->
                new SimulatedChannelProductionGuard(environment, "prod-secret")
                    .rejectSimulatedChannelConfiguration())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "SIMULATED channel signing material is only permitted in local, dev or test profiles");
  }
}
