package com.example.payments.trade.service.domain;

public enum OrderStatus {
  CREATED,
  PAYING,
  SUCCESS,
  FAILED,
  UNKNOWN,
  EXPIRED,
  CANCELED;

  public boolean isTerminal() {
    return this == SUCCESS || this == FAILED || this == EXPIRED || this == CANCELED;
  }

  public boolean canTransitionTo(OrderStatus next) {
    if (this == next) return true;
    if (isTerminal()) return false;
    return switch (next) {
      case CREATED -> this == CREATED;
      case PAYING -> this == CREATED || this == UNKNOWN;
      case SUCCESS -> this == CREATED || this == PAYING || this == UNKNOWN;
      case FAILED -> this == CREATED || this == PAYING || this == UNKNOWN;
      case UNKNOWN -> this == CREATED || this == PAYING;
      case EXPIRED -> this == CREATED || this == PAYING || this == UNKNOWN;
      case CANCELED -> this == CREATED || this == PAYING || this == UNKNOWN;
    };
  }
}
