package com.example.payments.gateway.service.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AdminAuthenticationFilter implements GlobalFilter, Ordered {
  private final SecretKey key;

  public AdminAuthenticationFilter(@Value("${gateway.security.jwt-secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    var request = exchange.getRequest();
    var path = request.getURI().getPath();
    if (!path.startsWith("/api/admin/") || path.equals("/api/admin/v1/auth/login")) {
      return chain.filter(exchange);
    }
    var authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return unauthorized(exchange);
    }
    try {
      var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(authorization.substring(7)).getPayload();
      var roles = claims.get("roles", List.class).stream().map(String::valueOf).toList();
      var authenticated = request.mutate().headers(headers -> {
        headers.set("X-User-Id", claims.getSubject());
        headers.set("X-Roles", String.join(",", roles));
      }).build();
      return chain.filter(exchange.mutate().request(authenticated).build());
    } catch (RuntimeException exception) {
      return unauthorized(exchange);
    }
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }
}
