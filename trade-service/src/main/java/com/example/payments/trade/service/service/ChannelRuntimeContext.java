package com.example.payments.trade.service.service;

import java.util.Map;
import java.util.Optional;

public record ChannelRuntimeContext(
    String channelId,
    String provider,
    String requestUrl,
    String signatureProfile,
    Map<String, Object> settings,
    Map<String, ChannelCredentialReference> credentialReferences,
    ChannelSecretResolver secretResolver) {
  public Optional<String> secret(String credentialRole) {
    var reference = credentialReferences.get(credentialRole);
    return reference == null ? Optional.empty() : Optional.of(secretResolver.resolve(reference));
  }
}
