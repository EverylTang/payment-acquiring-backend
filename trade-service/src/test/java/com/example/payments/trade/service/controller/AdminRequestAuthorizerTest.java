package com.example.payments.trade.service.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminRequestAuthorizerTest {
  private final AdminRequestAuthorizer authorizer = new AdminRequestAuthorizer("gateway-token");

  @Test
  void allowsTheRequiredOperationPermission() {
    assertDoesNotThrow(
        () ->
            authorizer.authorize(
                "gateway-token",
                "operator",
                encoded("outbox:list") + "," + encoded("outbox:redrive"),
                "outbox:redrive"));
  }

  @Test
  void rejectsRolesWhenTheOperationPermissionIsMissing() {
    assertThrows(
        ResponseStatusException.class,
        () ->
            authorizer.authorize(
                "gateway-token", "operator", encoded("ADMIN") + "," + encoded("OPS"), "outbox:redrive"));
  }

  private String encoded(String permission) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(permission.getBytes(StandardCharsets.UTF_8));
  }
}
