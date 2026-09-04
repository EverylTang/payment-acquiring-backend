package com.example.payments.trade.service.service;

import java.util.Map;

public record ChannelRuntimeContext(
    String channelId,
    String provider,
    String requestUrl,
    String signatureProfile,
    Map<String, Object> settings,
    Map<String, String> credentials) {
  public java.util.Optional<String> secret(String credentialRole) {
    return java.util.Optional.ofNullable(credentials.get(credentialRole)).filter(value -> !value.isBlank());
  }
}
