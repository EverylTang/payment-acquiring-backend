package com.example.payments.platform.service.model;

import java.time.Instant;

/** 商户回调配置持久化查询模型。 */
public record MerchantCallbackConfigModel(
    String merchantId,
    String successUrl,
    String failUrl,
    String notifyUrl,
    Instant createdAt,
    Instant updatedAt) {}
