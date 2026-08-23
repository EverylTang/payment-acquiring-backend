package com.example.payments.platform.service.model;

import java.time.Instant;

/** 逻辑产品持久化查询模型。 */
public record ProductModel(
    String productCode, String name, String status, Instant createdAt, Instant updatedAt) {}
