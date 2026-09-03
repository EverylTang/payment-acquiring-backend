package com.example.payments.platform.service.model;

import java.time.Instant;

/** 商户 API 凭证持久化查询模型。 */
public record MerchantCredentialModel(
    String merchantId, String apiKey, String apiSecretHash, Instant createdAt, Instant updatedAt) {}
