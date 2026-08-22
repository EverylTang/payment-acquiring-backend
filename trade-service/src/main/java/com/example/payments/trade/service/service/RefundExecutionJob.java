package com.example.payments.trade.service.service;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefundExecutionJob {
  private final RefundService service;

  @Scheduled(fixedDelayString = "${trade.refund.execute-ms:5000}")
  public void executeDue() {
    service.due(50).forEach(refund -> service.execute(refund.getRefundId()));
  }
}
