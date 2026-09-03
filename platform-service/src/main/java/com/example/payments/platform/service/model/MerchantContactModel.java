package com.example.payments.platform.service.model;

import java.time.Instant;

/** 商户联系人信息持久化查询模型。 */
public record MerchantContactModel(
    String merchantId,
    String contactName,
    String contactEmail,
    String contactPhone,
    Instant createdAt,
    Instant updatedAt) {}
