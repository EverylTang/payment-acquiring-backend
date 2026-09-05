package com.example.payments.trade.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentStateTransitionTest {
  @Test
  void successfulOrderCannotBeOverwritten() {
    assertThat(OrderStatus.SUCCESS.canTransitionTo(OrderStatus.FAILED)).isFalse();
    assertThat(OrderStatus.SUCCESS.canTransitionTo(OrderStatus.UNKNOWN)).isFalse();
    assertThat(OrderStatus.EXPIRED.canTransitionTo(OrderStatus.SUCCESS)).isFalse();
  }

  @Test
  void unknownOrderCanRecoverToSuccess() {
    assertThat(OrderStatus.UNKNOWN.canTransitionTo(OrderStatus.SUCCESS)).isTrue();
  }

  @Test
  void activeOrderCanExpire() {
    assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
    assertThat(OrderStatus.PAYING.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
    assertThat(OrderStatus.UNKNOWN.canTransitionTo(OrderStatus.EXPIRED)).isTrue();
  }

  @Test
  void successfulAttemptCannotTransitionButTimeoutCanRecover() {
    assertThat(PaymentAttemptStatus.SUCCESS.canTransitionTo(PaymentAttemptStatus.FAILED)).isFalse();
    assertThat(PaymentAttemptStatus.TIMEOUT.canTransitionTo(PaymentAttemptStatus.SUCCESS))
        .isTrue();
  }

  @Test
  void processingAttemptCanComplete() {
    assertThat(PaymentAttemptStatus.PROCESSING.canTransitionTo(PaymentAttemptStatus.SUCCESS))
        .isTrue();
    assertThat(PaymentAttemptStatus.PROCESSING.canTransitionTo(PaymentAttemptStatus.UNKNOWN))
        .isTrue();
  }
}
