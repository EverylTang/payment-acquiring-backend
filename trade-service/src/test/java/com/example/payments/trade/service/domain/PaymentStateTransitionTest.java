package com.example.payments.trade.service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentStateTransitionTest {
  @Test
  void successfulOrderCannotBeOverwritten() {
    assertThat(OrderStatus.SUCCESS.canTransitionTo(OrderStatus.FAILED)).isFalse();
    assertThat(OrderStatus.SUCCESS.canTransitionTo(OrderStatus.UNKNOWN)).isFalse();
  }

  @Test
  void unknownOrderCanRecoverToSuccess() {
    assertThat(OrderStatus.UNKNOWN.canTransitionTo(OrderStatus.SUCCESS)).isTrue();
  }

  @Test
  void terminalAttemptCannotTransition() {
    assertThat(PaymentAttemptStatus.SUCCESS.canTransitionTo(PaymentAttemptStatus.FAILED)).isFalse();
    assertThat(PaymentAttemptStatus.TIMEOUT.canTransitionTo(PaymentAttemptStatus.SUCCESS)).isFalse();
  }

  @Test
  void processingAttemptCanComplete() {
    assertThat(PaymentAttemptStatus.PROCESSING.canTransitionTo(PaymentAttemptStatus.SUCCESS)).isTrue();
    assertThat(PaymentAttemptStatus.PROCESSING.canTransitionTo(PaymentAttemptStatus.UNKNOWN)).isTrue();
  }
}
