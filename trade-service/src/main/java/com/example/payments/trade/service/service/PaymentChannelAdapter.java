package com.example.payments.trade.service.service;

public interface PaymentChannelAdapter {
  String provider();

  default boolean supportsSignatureProfile(String signatureProfile) {
    return true;
  }

  /** Providers with a protocol-specific signer can build their final payload before signing it. */
  default boolean ownsRequestSignature() {
    return false;
  }

  /** Validates provider-specific requirements before a payment attempt changes order state. */
  default void validateCreate(PaymentChannelRequest request) {}

  /** Whether the provider has a cancellation contract enabled for this adapter. */
  default boolean supportsCancellation() {
    return false;
  }

  /** Whether this adapter has an implemented provider refund contract. */
  default boolean supportsRefund() {
    return false;
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

  /**
   * Extracts the merchant order id for recovery when a callback arrives before create is recorded.
   */
  default String callbackMerchantOrderId(String rawPayload) {
    throw new UnsupportedOperationException("callback merchant order extraction is not configured");
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
      ChannelRequestSigner.ChannelRequestSignature signature,
      String callbackUrl,
      String subject,
      java.util.Map<String, String> payer) {}

  record PaymentChannelQuery(
      String attemptId,
      String orderId,
      String channelOrderId,
      String currency,
      String expectedAmount,
      ChannelRuntimeContext runtime) {}

  record PaymentCallbackRequest(
      String rawPayload,
      String signature,
      String callbackId,
      String expectedOrderId,
      String expectedCurrency,
      String expectedAmount,
      ChannelRuntimeContext runtime) {
    public PaymentCallbackRequest(
        String rawPayload, String signature, String callbackId, ChannelRuntimeContext runtime) {
      this(rawPayload, signature, callbackId, null, null, null, runtime);
    }
  }

  record PaymentChannelResult(
      String channelOrderId,
      String status,
      String responseSnapshot,
      String failureCode,
      String paymentUrl,
      String qrCode) {}

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
