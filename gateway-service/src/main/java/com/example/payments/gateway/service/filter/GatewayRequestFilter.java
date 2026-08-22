package com.example.payments.gateway.service.filter;

import java.util.List;
import java.util.UUID;
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
public class GatewayRequestFilter implements GlobalFilter, Ordered {
  private static final List<String> HOP_BY_HOP_HEADERS =
      List.of(
          HttpHeaders.CONNECTION,
          "Keep-Alive",
          HttpHeaders.PROXY_AUTHENTICATE,
          HttpHeaders.PROXY_AUTHORIZATION,
          HttpHeaders.TE,
          HttpHeaders.TRAILER,
          HttpHeaders.TRANSFER_ENCODING,
          HttpHeaders.UPGRADE);

  private final String internalToken;

  public GatewayRequestFilter(@Value("${gateway.security.internal-token:}") String internalToken) {
    this.internalToken = internalToken;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    var request = exchange.getRequest();
    var path = request.getURI().getPath();
    if (path.startsWith("/api/internal/")
        && (internalToken.isBlank()
            || !internalToken.equals(request.getHeaders().getFirst("X-Internal-Token")))) {
      exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
      return exchange.getResponse().setComplete();
    }

    var requestId = request.getHeaders().getFirst("X-Request-Id");
    if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
      requestId = UUID.randomUUID().toString();
    }
    var finalRequestId = requestId;
    var mutatedRequest =
        request
            .mutate()
            .headers(
                headers -> {
                  HOP_BY_HOP_HEADERS.forEach(headers::remove);
                  headers.remove("X-User-Id");
                  headers.remove("X-Merchant-Id");
                  headers.remove("X-Roles");
                  headers.remove("X-Gateway-Token");
                  if (path.startsWith("/api/admin/") && !internalToken.isBlank())
                    headers.set("X-Gateway-Token", internalToken);
                  headers.set("X-Request-Id", finalRequestId);
                })
            .build();
    exchange
        .getResponse()
        .beforeCommit(
            () -> {
              exchange.getResponse().getHeaders().set("X-Request-Id", finalRequestId);
              return Mono.empty();
            });
    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
