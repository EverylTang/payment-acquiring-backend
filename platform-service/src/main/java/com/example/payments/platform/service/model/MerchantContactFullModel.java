package com.example.payments.platform.service.model;

import java.time.Instant;

public record MerchantContactFullModel(
    Long id,
    String merchantId,
    String contactType,
    String contactName,
    String email,
    String phone,
    boolean notifyEnabled,
    Instant createdAt,
    Instant updatedAt) {}
