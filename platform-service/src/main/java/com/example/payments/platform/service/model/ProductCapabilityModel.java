package com.example.payments.platform.service.model;

import java.math.BigDecimal;

/** 产品能力持久化查询模型。 */
public record ProductCapabilityModel(
    String capabilityId,
    String productCode,
    String customerPaymentMethod,
    String channelPaymentMethod,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    boolean supportsRefund,
    String status) {}
