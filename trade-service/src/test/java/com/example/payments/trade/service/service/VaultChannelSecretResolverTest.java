package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VaultChannelSecretResolverTest {
  @Test
  void resolvesKvV2FieldWithVaultToken() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/secret/data/payments/channels/simulated",
        exchange -> {
          assertThat(exchange.getRequestHeaders().getFirst("X-Vault-Token")).isEqualTo("test-token");
          assertThat(exchange.getRequestURI().getQuery()).isEqualTo("version=1");
          var response = "{\"data\":{\"data\":{\"requestSigningKey\":\"test-secret\"}}}";
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
          exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    server.start();
    try {
      var resolver =
          new VaultChannelSecretResolver("http://127.0.0.1:" + server.getAddress().getPort(), "test-token");

      assertThat(
              resolver.resolve(
                  new ChannelCredentialReference(
                      "vault://secret/data/payments/channels/simulated#requestSigningKey", "v1")))
          .isEqualTo("test-secret");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsInvalidVaultReference() {
    var resolver = new VaultChannelSecretResolver("http://127.0.0.1:8200", "test-token");

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    new ChannelCredentialReference("vault://secret/payments/channel", "")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Vault 密钥引用格式无效");
  }
}
