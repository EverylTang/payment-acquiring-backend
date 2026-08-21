package com.example.payments.trade.service.application;

import com.example.payments.trade.service.config.AttemptQueryProperties;
import com.example.payments.trade.service.domain.PaymentAttemptStatus;
import com.example.payments.trade.service.infrastructure.persistence.PaymentAttemptRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentAttemptTimeoutJob {
  private final PaymentAttemptRepository repository;
  private final PaymentAttemptService service;
  private final AttemptQueryProperties properties;

  public PaymentAttemptTimeoutJob(PaymentAttemptRepository repository, PaymentAttemptService service,
      AttemptQueryProperties properties) {
    this.repository = repository;
    this.service = service;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${trade.attempt.timeout-scan-ms:30000}")
  public void compensate() {
    Instant now = Instant.now();
    repository.claimQueryable(now, properties.maxCount(), properties.batchSize(), properties.lockSeconds())
        .forEach(claim -> compensate(claim, now));
  }

  private void compensate(PaymentAttemptRepository.PaymentAttemptQueryClaim claim, Instant now) {
    int nextCount = claim.queryCount() + 1;
    try {
      var result = service.query(claim.attempt().attemptId());
      if (result.status() == PaymentAttemptStatus.PROCESSING) {
        if (nextCount >= properties.maxCount()) {
          service.timeout(claim.attempt().attemptId());
        } else {
          repository.completeQuery(claim.attempt().attemptId(), claim.claimToken(), now,
              now.plusSeconds(delaySeconds(nextCount)));
        }
      }
    } catch (RuntimeException exception) {
      repository.releaseQueryClaim(claim.attempt().attemptId(), claim.claimToken(), now,
          now.plusSeconds(delaySeconds(nextCount)));
    }
  }

  private long delaySeconds(int queryCount) {
    return Math.min(properties.retryMaxSeconds(),
        properties.retryBaseSeconds() * (1L << Math.min(Math.max(queryCount - 1, 0), 30)));
  }
}
