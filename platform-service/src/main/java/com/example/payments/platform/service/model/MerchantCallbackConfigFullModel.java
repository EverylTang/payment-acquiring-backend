package com.example.payments.platform.service.model;

import java.time.Instant;

public record MerchantCallbackConfigFullModel(
    String merchantId,
    String callbackUrl,
    String eventTypes,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
