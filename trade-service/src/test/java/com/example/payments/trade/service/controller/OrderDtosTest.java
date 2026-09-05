package com.example.payments.trade.service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OrderDtosTest {
  @Test
  void merchantOrderResponseDoesNotExposeInternalOrderState() {
    assertEquals(
        Arrays.asList(
            "orderId", "merchantOrderNo", "amount", "currency", "status", "expireAt", "paidAt"),
        Arrays.stream(OrderDtos.MerchantOrderResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .toList());
  }

  @Test
  void merchantAttemptResponseDoesNotExposeChannelSnapshots() {
    assertEquals(
        Arrays.asList(
            "attemptId", "orderId", "channelOrderId", "status", "failureCode", "paymentUrl", "qrCode"),
        Arrays.stream(OrderDtos.MerchantAttemptResponse.class.getRecordComponents())
            .map(component -> component.getName())
            .toList());
  }
}
