package com.example.payments.fund.service.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalRequestAuthorizerTest {
  @Test
  void acceptsOnlyTheConfiguredInternalToken() {
    var authorizer = new InternalRequestAuthorizer("internal-token");

    assertDoesNotThrow(() -> authorizer.authorize("internal-token"));
    assertThrows(ResponseStatusException.class, () -> authorizer.authorize("other-token"));
    assertThrows(ResponseStatusException.class, () -> authorizer.authorize(null));
  }
}
