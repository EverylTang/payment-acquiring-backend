package com.example.payments.trade.service.domain;

import java.time.Instant;

public record PaymentAttempt(
    String attemptId,
    String orderId,
    String channelId,
    String channelRequestNo,
    int attemptNo,
    PaymentAttemptStatus status,
    String requestSnapshot,
    String responseSnapshot,
    String failureCode,
    Instant startedAt,
    Instant completedAt,
    long version) {}
