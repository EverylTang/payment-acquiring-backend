package com.example.payments.trade.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.infrastructure.persistence.PaymentOrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderServiceTest {
  private final PaymentOrderRepository repository = org.mockito.Mockito.mock(PaymentOrderRepository.class);
  private final OrderService service = new OrderService(repository);

  @Test
  void duplicateMerchantOrderReturnsExistingOrder() {
    var command = new OrderService.CreateOrderCommand("m1", "o1", "p1", "CARD", "US", "USD",
        new BigDecimal("10.00"), "key-1", null);
    var existing = PaymentOrder.create("m1", "o1", "p1", "CARD", "US", "USD", new BigDecimal("10.00"), "key-1", command.expireAt());
    when(repository.findByIdempotency("m1", "key-1")).thenReturn(Optional.of(existing));
    assertThat(service.create(command).orderId()).isEqualTo(existing.orderId());
  }

  @Test
  void terminalSuccessCannotBeRolledBackByFailureCallback() {
    var success = PaymentOrder.create("m1", "o2", "p1", "CARD", "US", "USD", new BigDecimal("10.00"), "key-2", java.time.Instant.now().plusSeconds(1800))
        .withStatus(OrderStatus.SUCCESS, java.time.Instant.now());
    when(repository.findById("o2")).thenReturn(Optional.of(success));
    assertThat(service.callback("o2", OrderStatus.FAILED).status()).isEqualTo(OrderStatus.SUCCESS);
  }
}
