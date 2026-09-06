package com.example.payments.trade.service.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.payments.trade.service.model.PaymentRefundEntity;
import com.example.payments.trade.service.service.OrderService;
import com.example.payments.trade.service.service.RefundService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdminRefundControllerTest {
  private final RefundService refundService = mock(RefundService.class);
  private final AdminRefundController controller =
      new AdminRefundController(
          refundService, mock(OrderService.class), new AdminRequestAuthorizer("gateway-token"));

  @Test
  void rejectsRefundsThatDoNotBelongToTheRequestedOrder() {
    var refund = new PaymentRefundEntity();
    refund.setOrderId("order-b");
    when(refundService.get("refund-1")).thenReturn(refund);

    assertThrows(
        ResponseStatusException.class,
        () ->
            controller.get(
                "order-a", "refund-1", "gateway-token", "operator", encoded("order:list")));
  }

  private static String encoded(String permission) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(permission.getBytes(StandardCharsets.UTF_8));
  }
}
