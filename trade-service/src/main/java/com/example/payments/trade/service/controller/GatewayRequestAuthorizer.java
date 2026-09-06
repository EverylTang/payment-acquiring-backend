package com.example.payments.trade.service.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Ensures merchant identity headers are accepted only after gateway authentication. */
@Component
public class GatewayRequestAuthorizer {
  private final byte[] gatewayToken;

  public GatewayRequestAuthorizer(@Value("${trade.security.gateway-token:}") String gatewayToken) {
    this.gatewayToken = gatewayToken.getBytes(StandardCharsets.UTF_8);
  }

  public void authorize(String presentedToken) {
    if (gatewayToken.length == 0
        || presentedToken == null
        || !MessageDigest.isEqual(gatewayToken, presentedToken.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid gateway credential");
    }
  }
}
