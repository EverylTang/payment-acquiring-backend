package com.example.payments.trade.service.service;

import java.util.Map;

public record ChannelRuntimeContext(
    String channelId,
    String provider,
    String requestUrl,
    String signatureProfile,
    Map<String, Object> settings,
    Map<String, String> credentials,
    int schemaVersion) {
  public java.util.Optional<String> secret(String credentialRole) {
    return java.util.Optional.ofNullable(credentials.get(credentialRole))
        .filter(value -> !value.isBlank());
  }

  /** Resolves an operation-specific URL from the channel JSON without coupling it to a provider. */
  public String endpoint(String operation) {
    if ("create".equals(operation)) return requestUrl;
    Object value = settings.get(operation + "Url");
    if (value == null || String.valueOf(value).isBlank()) {
      throw new IllegalStateException("渠道未配置 " + operation + " 请求地址");
    }
    return String.valueOf(value).trim();
  }

  public String setting(String key) {
    Object value = settings.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }
}
