package com.example.payments.trade.service.service;

import org.springframework.stereotype.Component;

/** PayProo adapter retained for refund routing. Pay-in execution is no longer handled here. */
@Component
public class PayprooChannelAdapter implements PaymentChannelAdapter {
  @Override
  public String provider() {
    return "PAYPROO";
  }

  @Override
  public boolean supportsSignatureProfile(String signatureProfile) {
    return signatureProfile != null && !signatureProfile.isBlank();
  }

  @Override
  public PaymentRefundResult refundPayment(PaymentRefundRequest request) {
    throw new UnsupportedOperationException("PayProo 退款接口合同尚未配置");
  }
}
