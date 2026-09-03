package com.example.payments.platform.service.model;

import java.time.Instant;

/** 商户档案信息持久化查询模型。 */
public record MerchantProfileModel(
    String merchantId,
    String legalName,
    String businessType,
    String registeredCountry,
    String industry,
    String businessUrl,
    String productDescription,
    String statementDescriptor,
    String supportEmail,
    String supportPhone,
    String supportUrl,
    String addressLine1,
    String addressLine2,
    String addressCity,
    String addressState,
    String addressPostalCode,
    String riskLevel,
    String taxIdentifier,
    Instant createdAt,
    Instant updatedAt) {}
