package com.example.payments.trade.service.service;

public interface PaymentChannelAdapter {
  String provider();

  default boolean supportsSignatureProfile(String signatureProfile) {
    return true;
  }

  /** Whether this adapter has an implemented provider refund contract. */
  default boolean supportsRefund() {
    return false;
  }

  PaymentRefundResult refundPayment(PaymentRefundRequest request);

  default PaymentRefundCallback verifyRefundCallback(PaymentRefundCallbackRequest request) {
    throw new UnsupportedOperationException("refund callback verification is not configured");
  }

  record PaymentRefundRequest(
      String refundId,
      String orderId,
      String channelOrderId,
      String amount,
      String currency,
      ChannelRuntimeContext runtime) {}

  record PaymentRefundResult(
      String channelRefundId, String status, String responseSnapshot, String failureCode) {}

  record PaymentRefundCallbackRequest(
      String rawPayload,
      String signature,
      String callbackId,
      long timestamp,
      String nonce,
      ChannelRuntimeContext runtime) {}

  record PaymentRefundCallback(
      String callbackId, String refundId, String status, String rawPayload) {}

}
