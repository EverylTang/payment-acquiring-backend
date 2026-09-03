package com.example.payments.platform.service.model;

import java.time.Instant;

/** 商户持久化查询模型。 */
public record MerchantModel(
    String merchantId, String name, String status, Instant createdAt, Instant updatedAt) {}
