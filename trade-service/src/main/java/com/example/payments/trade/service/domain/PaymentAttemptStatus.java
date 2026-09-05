package com.example.payments.trade.service.domain;

public enum PaymentAttemptStatus {
  CREATED,
  PROCESSING,
  SUCCESS,
  FAILED,
  TIMEOUT,
  CANCELED,
  UNKNOWN;

  public boolean isTerminal() {
    // A provider can complete after our local query window. Keep TIMEOUT recoverable so a
    // verified late notification can still settle the order.
    return this == SUCCESS || this == FAILED || this == CANCELED;
  }

  public boolean canTransitionTo(PaymentAttemptStatus next) {
    if (this == next) return true;
    if (isTerminal()) return false;
    return switch (next) {
      case CREATED -> this == CREATED;
      case PROCESSING -> this == CREATED || this == UNKNOWN;
      case SUCCESS, FAILED, TIMEOUT, CANCELED ->
          this == CREATED || this == PROCESSING || this == UNKNOWN || this == TIMEOUT;
      case UNKNOWN -> this == CREATED || this == PROCESSING || this == TIMEOUT;
    };
  }
}
