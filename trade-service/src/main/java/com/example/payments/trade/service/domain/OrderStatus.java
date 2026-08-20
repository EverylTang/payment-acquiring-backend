package com.example.payments.trade.service.domain;

public enum OrderStatus {
  CREATED,
  PAYING,
  SUCCESS,
  FAILED,
  UNKNOWN,
  CANCELED;

  public boolean isTerminal() {
    return this == SUCCESS || this == FAILED || this == CANCELED;
  }
}
