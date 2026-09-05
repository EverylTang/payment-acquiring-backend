package com.example.payments.trade.service.mapper;

/** Event types which are delivered directly from the durable outbox instead of RocketMQ. */
public final class MerchantNotificationEventTypes {
  public static final String PAYMENT_NOTIFICATION = "MERCHANT_PAYMENT_NOTIFICATION";

  private MerchantNotificationEventTypes() {}
}
