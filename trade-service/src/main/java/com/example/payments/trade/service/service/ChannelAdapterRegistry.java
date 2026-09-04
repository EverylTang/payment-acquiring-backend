package com.example.payments.trade.service.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ChannelAdapterRegistry {
  private final List<PaymentChannelAdapter> adapters;

  public ChannelAdapterRegistry(List<PaymentChannelAdapter> adapters) {
    this.adapters = List.copyOf(adapters);
  }

  public PaymentChannelAdapter required(String provider, String signatureProfile) {
    return adapters.stream()
        .filter(adapter -> adapter.provider().equalsIgnoreCase(provider))
        .filter(adapter -> adapter.supportsSignatureProfile(signatureProfile))
        .findFirst()
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "未配置渠道适配器或签名方案: " + provider + "/" + signatureProfile));
  }

  public PaymentChannelAdapter required(String provider) {
    return required(provider, "DEFAULT");
  }
}
