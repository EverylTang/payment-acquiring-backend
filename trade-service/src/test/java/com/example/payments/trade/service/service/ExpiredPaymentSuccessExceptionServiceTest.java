package com.example.payments.trade.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.trade.service.mapper.ExpiredPaymentSuccessExceptionMapper;
import com.example.payments.trade.service.model.ExpiredPaymentSuccessExceptionEntity;
import org.junit.jupiter.api.Test;

class ExpiredPaymentSuccessExceptionServiceTest {
  private final ExpiredPaymentSuccessExceptionMapper mapper =
      org.mockito.Mockito.mock(ExpiredPaymentSuccessExceptionMapper.class);
  private final ExpiredPaymentSuccessExceptionService service =
      new ExpiredPaymentSuccessExceptionService(mapper);

  @Test
  void resolvesAnOpenExceptionWithAnOperatorAuditTrail() {
    var exception = new ExpiredPaymentSuccessExceptionEntity();
    exception.setExceptionId("exception-1");
    exception.setStatus("OPEN");
    when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(exception);

    var resolved = service.resolve("exception-1", "operator-1", "verified against channel settlement");

    assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
    assertThat(resolved.getResolvedBy()).isEqualTo("operator-1");
    assertThat(resolved.getResolution()).isEqualTo("verified against channel settlement");
    assertThat(resolved.getResolvedAt()).isNotNull();
    verify(mapper).updateById(exception);
  }
}
