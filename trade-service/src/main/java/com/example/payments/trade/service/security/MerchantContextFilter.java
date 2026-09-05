package com.example.payments.trade.service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MerchantContextFilter extends OncePerRequestFilter {
  private final byte[] gatewayToken;

  public MerchantContextFilter(@Value("${trade.security.gateway-token:}") String gatewayToken) {
    this.gatewayToken = gatewayToken.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/v1/payments/orders")
        || path.endsWith("/callback")
        || path.endsWith("/health");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws java.io.IOException, jakarta.servlet.ServletException {
    String supplied = request.getHeader("X-Gateway-Token");
    String merchantId = request.getHeader("X-Merchant-Id");
    if (gatewayToken.length == 0
        || supplied == null
        || merchantId == null
        || merchantId.isBlank()
        || !MessageDigest.isEqual(gatewayToken, supplied.getBytes(StandardCharsets.UTF_8))) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(request, response);
  }
}
