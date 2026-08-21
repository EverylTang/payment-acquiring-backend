package com.example.payments.trade.service.infrastructure.persistence;

import com.example.payments.trade.service.domain.PaymentAttempt;
import com.example.payments.trade.service.domain.PaymentAttemptStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentAttemptRepository {
  private final PaymentAttemptMapper mapper;

  public PaymentAttemptRepository(PaymentAttemptMapper mapper) { this.mapper = mapper; }

  public PaymentAttempt insert(PaymentAttempt attempt) {
    var entity = new PaymentAttemptEntity();
    entity.setAttemptId(attempt.attemptId()); entity.setOrderId(attempt.orderId()); entity.setChannelId(attempt.channelId());
    entity.setChannelRequestNo(attempt.channelRequestNo()); entity.setAttemptNo(attempt.attemptNo()); entity.setStatus(attempt.status().name());
    entity.setRequestSummary(attempt.requestSnapshot()); entity.setResponseSummary(attempt.responseSnapshot()); entity.setFailureCode(attempt.failureCode());
    entity.setStartedAt(toLocal(attempt.startedAt())); entity.setCompletedAt(attempt.completedAt() == null ? null : toLocal(attempt.completedAt())); entity.setVersion(0L);
    mapper.insert(entity); return attempt;
  }

  public Optional<PaymentAttempt> findByAttemptId(String attemptId) {
    return Optional.ofNullable(mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentAttemptEntity>()
        .eq(PaymentAttemptEntity::getAttemptId, attemptId).last("LIMIT 1"))).map(this::toDomain);
  }

  public Optional<PaymentAttempt> findByChannelRequestNo(String channelId, String requestNo) {
    return Optional.ofNullable(mapper.findByChannelOrderId(requestNo)).filter(entity -> channelId.equals(entity.getChannelId())).map(this::toDomain);
  }

  public int countByOrderId(String orderId) {
    return mapper.countByOrderId(orderId);
  }

  public java.util.List<PaymentAttempt> findProcessingBefore(Instant before) {
    return mapper.findProcessingBefore(toLocal(before)).stream().map(this::toDomain).toList();
  }

  public boolean update(PaymentAttemptStatus expected, PaymentAttempt attempt) {
    return mapper.updateAttempt(attempt.attemptId(), expected.name(), attempt.status().name(), attempt.responseSnapshot(),
        attempt.failureCode(), attempt.completedAt() == null ? null : toLocal(attempt.completedAt())) == 1;
  }

  private PaymentAttempt toDomain(PaymentAttemptEntity e) {
    return new PaymentAttempt(e.getAttemptId(), e.getOrderId(), e.getChannelId(), e.getChannelRequestNo(), e.getAttemptNo(),
        PaymentAttemptStatus.valueOf(e.getStatus()), e.getRequestSummary(), e.getResponseSummary(), e.getFailureCode(),
        toInstant(e.getStartedAt()), e.getCompletedAt() == null ? null : toInstant(e.getCompletedAt()));
  }
  private static LocalDateTime toLocal(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
  private static Instant toInstant(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC); }
}
