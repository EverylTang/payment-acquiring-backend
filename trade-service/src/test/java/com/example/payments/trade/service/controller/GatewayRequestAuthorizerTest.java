package com.example.payments.trade.service.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GatewayRequestAuthorizerTest {
  private final GatewayRequestAuthorizer authorizer = new GatewayRequestAuthorizer("gateway-token");

  @Test
  void acceptsOnlyTheGatewayToken() {
    assertDoesNotThrow(() -> authorizer.authorize("gateway-token"));
    assertThrows(ResponseStatusException.class, () -> authorizer.authorize("forged-token"));
    assertThrows(ResponseStatusException.class, () -> authorizer.authorize(null));
  }

  @Test
  void rejectsRequestsWhenTheGatewayTokenIsNotConfigured() {
    assertThrows(
        ResponseStatusException.class, () -> new GatewayRequestAuthorizer("").authorize("anything"));
  }
}
