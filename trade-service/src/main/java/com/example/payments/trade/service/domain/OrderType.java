package com.example.payments.trade.service.domain;

/** Immutable transaction direction captured when an order is created. */
public enum OrderType {
  PAYIN("PI"),
  PAYOUT("PO");

  private final String numberPrefix;

  OrderType(String numberPrefix) {
    this.numberPrefix = numberPrefix;
  }

  public String numberPrefix() {
    return numberPrefix;
  }

  public static OrderType fromProductType(String productType) {
    try {
      return valueOf(productType);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new IllegalArgumentException("产品类型必须是 PAYIN 或 PAYOUT");
    }
  }
}
