package com.example.payments.platform.service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.payments.platform.service.mapper.ConfigurationAdminMapper;
import com.example.payments.platform.service.mapper.PricingRuleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

class ConfigurationAdminServiceTest {
  @Test
  void rejectsSimulatedProviderOutsideLocalDevAndTestProfiles() {
    var environment = Mockito.mock(Environment.class);
    Mockito.when(environment.getActiveProfiles()).thenReturn(new String[] { "prod" });
    var service = new ConfigurationAdminService(
        Mockito.mock(ConfigurationAdminMapper.class),
        Mockito.mock(OperationAuditService.class),
        new ObjectMapper(),
        Mockito.mock(PricingRuleMapper.class),
        environment);

    assertThatThrownBy(
        () -> service.createChannel(
            simulatedChannel(),
            Mockito.mock(org.springframework.security.core.Authentication.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SIMULATED channels are only permitted in local, dev or test profiles");
  }

  @Test
  void allowsSimulatedProviderInDevProfile() {
    var environment = Mockito.mock(Environment.class);
    Mockito.when(environment.getActiveProfiles()).thenReturn(new String[] { "dev" });
    var authentication = Mockito.mock(org.springframework.security.core.Authentication.class);
    Mockito.when(authentication.getName()).thenReturn("tester");
    var service = new ConfigurationAdminService(
        Mockito.mock(ConfigurationAdminMapper.class),
        Mockito.mock(OperationAuditService.class),
        new ObjectMapper(),
        Mockito.mock(PricingRuleMapper.class),
        environment);

    org.assertj.core.api.Assertions.assertThatCode(
        () -> service.createChannel(simulatedChannel(), authentication))
        .doesNotThrowAnyException();
  }

  private static ConfigurationAdminService.ChannelRequest simulatedChannel() {
    return new ConfigurationAdminService.ChannelRequest(
        "simulated-channel",
        "Simulated channel",
        "SIMULATED",
        "https://simulated.local/pay",
        "SIMULATED_SHA256_PREFIX_V1",
        Map.of(),
        Map.of(),
        "US",
        "USD",
        "CARD",
        new BigDecimal("0.01"),
        new BigDecimal("100.00"));
  }
}
