package com.example.payments.trade.service.application;

import com.example.payments.trade.service.domain.PaymentAttempt;

public interface PaymentChannelAdapter {
  PaymentChannelResult createPayment(PaymentChannelRequest request);

  PaymentChannelResult queryPayment(PaymentChannelQuery request);

  PaymentChannelResult cancelPayment(PaymentChannelQuery request);

  PaymentCallback verifyCallback(PaymentCallbackRequest request);

  record PaymentChannelRequest(String attemptId, String orderId, String merchantId, String currency,
      String paymentMethod, String amount, String behavior) {}

  record PaymentChannelQuery(String attemptId, String channelOrderId) {}

  record PaymentCallbackRequest(String rawPayload, String signature, String callbackId) {}

  record PaymentChannelResult(String channelOrderId, String status, String responseSnapshot,
      String failureCode, String paymentUrl) {}

  record PaymentCallback(String callbackId, String channelOrderId, String status, String rawPayload) {}
}
