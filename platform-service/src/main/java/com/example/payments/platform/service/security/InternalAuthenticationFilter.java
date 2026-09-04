package com.example.payments.platform.service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalAuthenticationFilter extends OncePerRequestFilter {
  private final byte[] internalToken;

  public InternalAuthenticationFilter(@Value("${platform.security.internal-token:}") String token) {
    internalToken = token.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/internal/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var supplied = request.getHeader("X-Internal-Token");
    var authorized =
        internalToken.length > 0
            && supplied != null
            && MessageDigest.isEqual(internalToken, supplied.getBytes(StandardCharsets.UTF_8));
    if (!authorized) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"内部接口访问被拒绝\"}");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
