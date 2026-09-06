package com.example.payments.fund.service.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Authorizes service-to-service endpoints when Fund is reached without the gateway. */
@Component
public class InternalRequestAuthorizer {
  private final byte[] gatewayToken;

  public InternalRequestAuthorizer(@Value("${fund.security.gateway-token:}") String gatewayToken) {
    this.gatewayToken = gatewayToken.getBytes(StandardCharsets.UTF_8);
  }

  public void authorize(String presentedToken) {
    if (gatewayToken.length == 0
        || presentedToken == null
        || !MessageDigest.isEqual(gatewayToken, presentedToken.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid internal credential");
    }
  }
}
