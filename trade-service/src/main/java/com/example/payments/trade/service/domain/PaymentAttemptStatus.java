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
    return this == SUCCESS || this == FAILED || this == TIMEOUT || this == CANCELED;
  }

  public boolean canTransitionTo(PaymentAttemptStatus next) {
    if (this == next) return true;
    if (isTerminal()) return false;
    return switch (next) {
      case CREATED -> this == CREATED;
      case PROCESSING -> this == CREATED || this == UNKNOWN;
      case SUCCESS, FAILED, TIMEOUT, CANCELED ->
          this == CREATED || this == PROCESSING || this == UNKNOWN;
      case UNKNOWN -> this == CREATED || this == PROCESSING;
    };
  }
}
