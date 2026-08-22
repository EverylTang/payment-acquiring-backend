package com.example.payments.trade.service.application;


public interface PaymentChannelAdapter {
  PaymentChannelResult createPayment(PaymentChannelRequest request);

  PaymentChannelResult queryPayment(PaymentChannelQuery request);

  PaymentChannelResult cancelPayment(PaymentChannelQuery request);

  PaymentRefundResult refundPayment(PaymentRefundRequest request);

  default PaymentRefundCallback verifyRefundCallback(PaymentRefundCallbackRequest request) {
    throw new UnsupportedOperationException("refund callback verification is not configured");
  }

  PaymentCallback verifyCallback(PaymentCallbackRequest request);

  record PaymentChannelRequest(
      String attemptId,
      String orderId,
      String merchantId,
      String currency,
      String paymentMethod,
      String amount,
      String behavior) {}

  record PaymentChannelQuery(String attemptId, String channelOrderId) {}

  record PaymentCallbackRequest(String rawPayload, String signature, String callbackId) {}

  record PaymentChannelResult(
      String channelOrderId,
      String status,
      String responseSnapshot,
      String failureCode,
      String paymentUrl) {}

  record PaymentRefundRequest(
      String refundId, String orderId, String channelOrderId, String amount, String currency) {}

  record PaymentRefundResult(
      String channelRefundId, String status, String responseSnapshot, String failureCode) {}

  record PaymentRefundCallbackRequest(
      String rawPayload, String signature, String callbackId, long timestamp, String nonce) {}

  record PaymentRefundCallback(
      String callbackId, String refundId, String status, String rawPayload) {}

  record PaymentCallback(
      String callbackId, String channelOrderId, String status, String rawPayload) {}
}
