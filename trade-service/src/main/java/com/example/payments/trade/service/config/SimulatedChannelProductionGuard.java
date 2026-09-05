package com.example.payments.trade.service.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Rejects simulation secrets outside the explicitly non-production execution profiles. */
@Component
public class SimulatedChannelProductionGuard {
  private final Environment environment;
  private final String simulatedSigningSecret;

  public SimulatedChannelProductionGuard(
      Environment environment,
      @Value("${trade.channel.simulated.signing-secret:}") String simulatedSigningSecret) {
    this.environment = environment;
    this.simulatedSigningSecret = simulatedSigningSecret;
  }

  @PostConstruct
  void rejectSimulatedChannelConfiguration() {
    boolean simulationProfile =
        Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));
    if (!simulationProfile && !simulatedSigningSecret.isBlank()) {
      throw new IllegalStateException(
          "SIMULATED channel signing material is only permitted in local or test profiles");
    }
  }
}
