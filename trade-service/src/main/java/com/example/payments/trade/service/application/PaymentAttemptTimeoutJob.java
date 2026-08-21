package com.example.payments.trade.service.application;

import com.example.payments.trade.service.domain.PaymentAttemptStatus;
import com.example.payments.trade.service.infrastructure.persistence.PaymentAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentAttemptTimeoutJob {
  private final PaymentAttemptRepository repository;
  private final PaymentAttemptService service;

  public PaymentAttemptTimeoutJob(PaymentAttemptRepository repository, PaymentAttemptService service) {
    this.repository = repository;
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${trade.attempt.timeout-scan-ms:30000}")
  public void compensate() {
    repository.findProcessingBefore(Instant.now().minus(Duration.ofMinutes(5))).forEach(attempt -> {
      if (attempt.status() == PaymentAttemptStatus.PROCESSING) service.query(attempt.attemptId());
    });
  }
}
