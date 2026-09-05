package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class MerchantCallbackUrlPolicyTest {
  private final MerchantCallbackUrlPolicy policy = new MerchantCallbackUrlPolicy(false);

  @Test
  void allowsPublicHttpsCallback() {
    assertThat(policy.validate("https://8.8.8.8/payment", "notifyUrl"))
        .isEqualTo("https://8.8.8.8/payment");
  }

  @Test
  void rejectsHttpAndPrivateOrLoopbackTargets() {
    assertThatThrownBy(() -> policy.validate("http://8.8.8.8/payment", "notifyUrl"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> policy.validate("https://127.0.0.1/payment", "notifyUrl"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> policy.validate("https://169.254.169.254/latest/meta-data", "notifyUrl"))
        .isInstanceOf(ResponseStatusException.class);
  }
}
