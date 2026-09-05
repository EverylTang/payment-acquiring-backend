package com.example.payments.trade.service.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.trade.service.mapper.PaymentOutboxEventRepository;
import com.example.payments.trade.service.model.PaymentOutboxEventEntity;
import org.junit.jupiter.api.Test;

class PaymentOutboxAdminServiceTest {
  private final PaymentOutboxEventRepository repository =
      org.mockito.Mockito.mock(PaymentOutboxEventRepository.class);
  private final MerchantNotificationOutboxService notificationOutbox =
      org.mockito.Mockito.mock(MerchantNotificationOutboxService.class);
  private final PaymentOutboxAdminService service =
      new PaymentOutboxAdminService(repository, notificationOutbox);

  @Test
  void redrivenMerchantNotificationIsAuditedAndMadeDeliverable() {
    PaymentOutboxEventEntity event = new PaymentOutboxEventEntity();
    event.setEventId("notification-1");
    event.setEventType(MerchantNotificationOutboxService.EVENT_TYPE);
    event.setStatus("DEAD");
    when(repository.findByEventId("notification-1")).thenReturn(event);
    when(repository.redrive(org.mockito.ArgumentMatchers.eq("notification-1"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(true);

    service.redrive("notification-1", "operator-1", "merchant endpoint restored", "request-1");

    verify(repository)
        .insertAudit(
            org.mockito.ArgumentMatchers.eq("notification-1"),
            org.mockito.ArgumentMatchers.eq("operator-1"),
            org.mockito.ArgumentMatchers.eq("merchant endpoint restored"),
            org.mockito.ArgumentMatchers.eq("DEAD"),
            org.mockito.ArgumentMatchers.eq("RETRYING"),
            org.mockito.ArgumentMatchers.eq("request-1"),
            org.mockito.ArgumentMatchers.any());
    verify(notificationOutbox).redriven(event);
  }
}
