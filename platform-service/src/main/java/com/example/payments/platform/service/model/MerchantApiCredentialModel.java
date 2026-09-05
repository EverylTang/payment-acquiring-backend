package com.example.payments.platform.service.model;

import java.time.Instant;

public record MerchantApiCredentialModel(
    String credentialId,
    String merchantId,
    String secretCiphertext,
    String ipAllowlist,
    Instant expiresAt) {}
