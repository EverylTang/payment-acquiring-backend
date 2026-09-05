package com.example.payments.gateway.service.filter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.security.MessageDigest;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MerchantAuthenticationFilter implements GlobalFilter, Ordered {
  private final WebClient platformClient;
  private final String internalToken;

  public MerchantAuthenticationFilter(
      @Value("${gateway.merchant-auth.platform-base-url:http://127.0.0.1:8081}")
          String platformBaseUrl,
      @Value("${gateway.security.internal-token:}") String internalToken) {
    platformClient = WebClient.builder().baseUrl(platformBaseUrl).build();
    this.internalToken = internalToken;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    var request = exchange.getRequest();
    if (!merchantRoute(request.getURI().getPath())) return chain.filter(exchange);
    String keyId = request.getHeaders().getFirst("X-Merchant-Key-Id");
    String timestamp = request.getHeaders().getFirst("X-Merchant-Timestamp");
    String nonce = request.getHeaders().getFirst("X-Merchant-Nonce");
    String signature = request.getHeaders().getFirst("X-Merchant-Signature");
    if (keyId == null
        || timestamp == null
        || nonce == null
        || signature == null
        || internalToken.isBlank()) return reject(exchange);
    return DataBufferUtils.join(request.getBody())
        .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
        .flatMap(
            buffer -> {
              byte[] body = new byte[buffer.readableByteCount()];
              buffer.read(body);
              DataBufferUtils.release(buffer);
              long epoch;
              try {
                epoch = Long.parseLong(timestamp);
              } catch (NumberFormatException exception) {
                return reject(exchange);
              }
              String canonical =
                  String.join(
                      "\n",
                      request.getMethod().name(),
                      request.getURI().getRawPath(),
                      normalizedQuery(request.getURI().getRawQuery()),
                      timestamp,
                      nonce,
                      sha256(body));
              String sourceIp =
                  request.getRemoteAddress() == null
                          || request.getRemoteAddress().getAddress() == null
                      ? "unknown"
                      : request.getRemoteAddress().getAddress().getHostAddress();
              var payload = new VerifyRequest(keyId, epoch, nonce, signature, sourceIp, canonical);
              return platformClient
                  .post()
                  .uri("/api/internal/v1/merchant-authentication/verify")
                  .header("X-Internal-Token", internalToken)
                  .bodyValue(payload)
                  .exchangeToMono(
                      response ->
                          response.statusCode().is2xxSuccessful()
                              ? response.bodyToMono(VerifiedMerchant.class)
                              : Mono.empty())
                  .flatMap(
                      verified ->
                          chain.filter(
                              exchange
                                  .mutate()
                                  .request(
                                      new AuthenticatedRequest(
                                          request, body, verified, internalToken))
                                  .build()))
                  .switchIfEmpty(reject(exchange))
                  .onErrorResume(exception -> reject(exchange));
            });
  }

  private static boolean merchantRoute(String path) {
    return path.startsWith("/api/v1/payments/orders")
        && !path.endsWith("/callback")
        && !path.endsWith("/health");
  }

  private static String normalizedQuery(String query) {
    if (query == null || query.isBlank()) return "";
    return Arrays.stream(query.split("&", -1))
        .sorted()
        .reduce((left, right) -> left + "&" + right)
        .orElse("");
  }

  private static String sha256(byte[] body) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static Mono<Void> reject(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 20;
  }

  private record VerifyRequest(
      String keyId,
      long timestamp,
      String nonce,
      String signature,
      String sourceIp,
      String canonicalPayload) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record VerifiedMerchant(String merchantId, String keyId) {}

  private static class AuthenticatedRequest extends ServerHttpRequestDecorator {
    private final byte[] body;
    private final VerifiedMerchant merchant;
    private final String gatewayToken;

    AuthenticatedRequest(
        org.springframework.http.server.reactive.ServerHttpRequest request,
        byte[] body,
        VerifiedMerchant merchant,
        String gatewayToken) {
      super(request);
      this.body = body;
      this.merchant = merchant;
      this.gatewayToken = gatewayToken;
    }

    @Override
    public org.springframework.http.HttpHeaders getHeaders() {
      var headers = new org.springframework.http.HttpHeaders();
      headers.putAll(super.getHeaders());
      headers.remove("X-Merchant-Key-Id");
      headers.remove("X-Merchant-Timestamp");
      headers.remove("X-Merchant-Nonce");
      headers.remove("X-Merchant-Signature");
      headers.set("X-Merchant-Id", merchant.merchantId());
      headers.set("X-Gateway-Token", gatewayToken);
      return headers;
    }

    @Override
    public Flux<org.springframework.core.io.buffer.DataBuffer> getBody() {
      return Flux.just(
          new org.springframework.core.io.buffer.DefaultDataBufferFactory().wrap(body));
    }
  }
}
