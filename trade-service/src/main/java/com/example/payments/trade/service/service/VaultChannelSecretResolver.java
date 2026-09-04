package com.example.payments.trade.service.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Resolves HashiCorp Vault KV v2 references in the form vault://mount/data/path#field. */
@Component
@ConditionalOnProperty(name = "trade.secrets.provider", havingValue = "vault")
class VaultChannelSecretResolver implements ChannelSecretResolver {
  private final RestClient client;
  private final String token;
  private final Path tokenFile;

  VaultChannelSecretResolver(String address, String token) {
    this(address, token, "", 1000, 2000, "dev");
  }

  @Autowired
  VaultChannelSecretResolver(
      @Value("${trade.secrets.vault.address:}") String address,
      @Value("${trade.secrets.vault.token:}") String token,
      @Value("${trade.secrets.vault.token-file:}") String tokenFile,
      @Value("${trade.secrets.vault.connect-timeout-ms:1000}") long connectTimeoutMs,
      @Value("${trade.secrets.vault.read-timeout-ms:2000}") long readTimeoutMs,
      @Value("${spring.profiles.active:dev}") String activeProfiles) {
    if (address == null
        || address.isBlank()
        || ((token == null || token.isBlank()) && (tokenFile == null || tokenFile.isBlank()))) {
      throw new IllegalStateException("Vault 地址或访问令牌未配置");
    }
    if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
      throw new IllegalStateException("Vault 超时配置必须大于 0");
    }
    var vaultAddress = URI.create(address);
    if (activeProfiles.contains("prod") && !"https".equalsIgnoreCase(vaultAddress.getScheme())) {
      throw new IllegalStateException("生产环境必须使用 HTTPS Vault 地址");
    }
    var requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build());
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.client = RestClient.builder().baseUrl(address).requestFactory(requestFactory).build();
    this.token = token == null ? "" : token;
    this.tokenFile = tokenFile == null || tokenFile.isBlank() ? null : Path.of(tokenFile);
  }

  @Override
  public String resolve(ChannelCredentialReference credential) {
    var reference = parse(credential.secretReference());
    try {
      var response =
          client
              .get()
              .uri(
                  builder -> {
                    builder.path("/v1/{mount}{path}");
                    var version = vaultVersion(credential.keyVersion());
                    if (version != null) builder.queryParam("version", version);
                    return builder.build(reference.mount(), reference.path());
                  })
              .header("X-Vault-Token", token())
              .retrieve()
              .body(Map.class);
      return secret(response, reference.field());
    } catch (RestClientException exception) {
      throw new IllegalStateException("无法从 Vault 读取渠道密钥", exception);
    }
  }

  private String token() {
    if (tokenFile == null) return token;
    try {
      var value = Files.readString(tokenFile).trim();
      if (value.isBlank()) throw new IllegalStateException("Vault 令牌文件为空");
      return value;
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("无法读取 Vault 令牌文件", exception);
    }
  }

  private Integer vaultVersion(String keyVersion) {
    if (keyVersion == null || keyVersion.isBlank()) return null;
    var normalized = keyVersion.startsWith("v") ? keyVersion.substring(1) : keyVersion;
    if (!normalized.matches("[1-9][0-9]*")) {
      throw new IllegalArgumentException("Vault KV 密钥版本必须是正整数或 v 加正整数");
    }
    try {
      return Integer.valueOf(normalized);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Vault KV 密钥版本超出范围", exception);
    }
  }

  private VaultReference parse(String secretReference) {
    try {
      var uri = URI.create(secretReference);
      var mount = uri.getHost();
      var path = uri.getPath();
      var field = uri.getFragment();
      if (!"vault".equals(uri.getScheme())
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || mount == null
          || !mount.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
          || path == null
          || !path.matches("/data/[A-Za-z0-9][A-Za-z0-9_./-]*")
          || path.contains("..")
          || field == null
          || !field.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
        throw new IllegalArgumentException("Vault 密钥引用格式无效");
      }
      return new VaultReference(mount, path, field);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Vault 密钥引用格式无效", exception);
    }
  }

  private String secret(Map<?, ?> response, String field) {
    if (response == null
        || !(response.get("data") instanceof Map<?, ?> envelope)
        || !(envelope.get("data") instanceof Map<?, ?> values)
        || !(values.get(field) instanceof String value)
        || value.isBlank()) {
      throw new IllegalStateException("Vault 未返回渠道密钥");
    }
    return value;
  }

  private record VaultReference(String mount, String path, String field) {}
}
