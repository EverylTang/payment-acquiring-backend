package com.example.payments.platform.service.model;

import java.time.Instant;

/** 逻辑产品持久化查询模型。 */
public record ProductModel(
    String productCode,
    String name,
    String productType,
    String accessMode,
    String defaultCountry,
    String defaultCurrency,
    String description,
    String statementDescriptor,
    String status,
    Long activeCapabilityCount,
    String supportedCurrencies,
    String supportedPaymentMethods,
    Instant createdAt,
    Instant updatedAt) {}
