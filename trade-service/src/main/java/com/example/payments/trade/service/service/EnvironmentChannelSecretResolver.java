package com.example.payments.trade.service.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local development resolver. Production deployments replace this with a Vault or cloud KMS
 * adapter.
 */
@Component
@ConditionalOnProperty(name = "trade.secrets.provider", havingValue = "env", matchIfMissing = true)
class EnvironmentChannelSecretResolver implements ChannelSecretResolver {
  @Override
  public String resolve(ChannelCredentialReference reference) {
    var secretReference = reference.secretReference();
    if (!secretReference.startsWith("env://")) {
      throw new IllegalStateException("当前环境未配置该渠道密钥管理服务: " + secretReference);
    }
    var variable = secretReference.substring("env://".length());
    if (!variable.matches("[A-Z][A-Z0-9_]*")) {
      throw new IllegalArgumentException("环境变量密钥引用格式无效");
    }
    var secret = System.getenv(variable);
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("渠道密钥引用未配置: " + variable);
    }
    return secret;
  }
}
