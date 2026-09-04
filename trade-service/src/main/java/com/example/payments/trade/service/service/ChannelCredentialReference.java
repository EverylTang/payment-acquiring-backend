package com.example.payments.trade.service.service;

public record ChannelCredentialReference(String secretReference, String keyVersion) {
  public ChannelCredentialReference {
    if (secretReference == null || secretReference.isBlank()) {
      throw new IllegalArgumentException("渠道密钥引用不能为空");
    }
    keyVersion = keyVersion == null ? "" : keyVersion.trim();
  }
}
