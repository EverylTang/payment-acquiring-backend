package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.payments.trade.service.domain.OrderType;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class OrderNumberGeneratorTest {
  @Test
  void generatesReadableUniqueNumbersForBothTransactionTypes() {
    var generator = new OrderNumberGenerator(7, () -> 1_725_384_000_123L);
    var numbers = new HashSet<String>();
    for (int index = 0; index < 20_000; index++) {
      numbers.add(generator.next(index % 2 == 0 ? OrderType.PAYIN : OrderType.PAYOUT));
    }

    assertThat(numbers).hasSize(20_000);
    assertThat(numbers).allMatch(number -> number.matches("P[IO]\\d{17}07[a-z0-9]{5}\\d{5}"));
  }
}
