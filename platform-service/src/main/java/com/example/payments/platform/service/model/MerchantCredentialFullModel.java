package com.example.payments.platform.service.model;

import java.time.Instant;

public record MerchantCredentialFullModel(
    String credentialId,
    String merchantId,
    String credentialType,
    String secretHash,
    String secretHint,
    String status,
    Instant createdAt,
    Instant rotatedAt,
    Instant revokedAt) {}
