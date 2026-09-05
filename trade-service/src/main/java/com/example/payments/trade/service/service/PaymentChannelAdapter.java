package com.example.payments.trade.service.service;

public interface PaymentChannelAdapter {
  String provider();

  default boolean supportsSignatureProfile(String signatureProfile) {
    return true;
  }

  PaymentChannelResult createPayment(PaymentChannelRequest request);

  PaymentChannelResult queryPayment(PaymentChannelQuery request);

  PaymentChannelResult cancelPayment(PaymentChannelQuery request);

  PaymentRefundResult refundPayment(PaymentRefundRequest request);

  default PaymentRefundCallback verifyRefundCallback(PaymentRefundCallbackRequest request) {
    throw new UnsupportedOperationException("refund callback verification is not configured");
  }

  /**
   * Extracts the provider order identifier only to locate the credential snapshot for verification.
   */
  default String callbackChannelOrderId(String rawPayload) {
    throw new UnsupportedOperationException("callback order extraction is not configured");
  }

  PaymentCallback verifyCallback(PaymentCallbackRequest request);

  record PaymentChannelRequest(
      String attemptId,
      String orderId,
      String merchantId,
      String currency,
      String paymentMethod,
      String amount,
      ChannelRuntimeContext runtime,
      ChannelRequestSigner.ChannelRequestSignature signature) {}

  record PaymentChannelQuery(String attemptId, String channelOrderId) {}

  record PaymentCallbackRequest(
      String rawPayload, String signature, String callbackId, ChannelRuntimeContext runtime) {}

  record PaymentChannelResult(
      String channelOrderId,
      String status,
      String responseSnapshot,
      String failureCode,
      String paymentUrl) {}

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

  record PaymentCallback(
      String callbackId, String channelOrderId, String status, String rawPayload) {}
}
