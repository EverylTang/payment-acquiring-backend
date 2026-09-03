package com.example.payments.platform.service.model;

import java.time.Instant;

/** 商户档案信息持久化查询模型。 */
public record MerchantProfileModel(
    String merchantId,
    String legalName,
    String registeredCountry,
    String industry,
    String riskLevel,
    String taxIdentifier,
    Instant createdAt,
    Instant updatedAt) {}
