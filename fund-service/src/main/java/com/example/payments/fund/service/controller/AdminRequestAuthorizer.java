package com.example.payments.fund.service.controller;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminRequestAuthorizer {
  private final String gatewayToken;

  public AdminRequestAuthorizer(@Value("${fund.security.gateway-token:}") String gatewayToken) {
    this.gatewayToken = gatewayToken;
  }

  public void authorize(String presentedToken, String userId, String roles) {
    if (gatewayToken.isBlank() || !gatewayToken.equals(presentedToken)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid gateway credential");
    }
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "administrator identity is required");
    }
    boolean allowed = roles != null && Arrays.stream(roles.split(","))
        .map(String::trim).anyMatch(role -> role.equals("ADMIN") || role.equals("OPS"));
    if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "administrator role is required");
  }
}
