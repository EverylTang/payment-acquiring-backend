package com.example.payments.trade.service.controller;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminRequestAuthorizer {
  private final String gatewayToken;

  public AdminRequestAuthorizer(@Value("${trade.security.gateway-token:}") String gatewayToken) {
    this.gatewayToken = gatewayToken;
  }

  public void authorize(
      String presentedToken, String userId, String permissions, String requiredPermission) {
    if (gatewayToken.isBlank() || !gatewayToken.equals(presentedToken)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid gateway credential");
    }
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "administrator identity is required");
    }
    boolean allowed =
        permissions != null
            && Arrays.stream(permissions.split(","))
                .map(String::trim)
                .anyMatch(encodePermission(requiredPermission)::equals);
    if (!allowed)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "operation permission is required");
  }

  private String encodePermission(String permission) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(permission.getBytes(StandardCharsets.UTF_8));
  }
}
