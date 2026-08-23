package com.example.payments.platform.service.model;

import java.time.Instant;

public record OperationAuditModel(
    String auditId,
    String operatorId,
    String action,
    String resourceType,
    String resourceId,
    String requestId,
    String reason,
    String beforeSummary,
    String afterSummary,
    Instant createdAt) {}
