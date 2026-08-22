package com.example.payments.trade.service.mapper;

import com.example.payments.trade.service.domain.PaymentAttempt;
import com.example.payments.trade.service.domain.PaymentAttemptStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentAttemptRepository {
  private final PaymentAttemptMapper mapper;
  private final String queryWorkerId = UUID.randomUUID().toString();

  public PaymentAttemptRepository(PaymentAttemptMapper mapper) { this.mapper = mapper; }

  public PaymentAttempt insert(PaymentAttempt attempt) {
    var entity = new PaymentAttemptEntity();
    entity.setAttemptId(attempt.attemptId()); entity.setOrderId(attempt.orderId()); entity.setChannelId(attempt.channelId());
    entity.setChannelRequestNo(attempt.channelRequestNo()); entity.setAttemptNo(attempt.attemptNo()); entity.setStatus(attempt.status().name());
    entity.setRequestSummary(attempt.requestSnapshot()); entity.setResponseSummary(attempt.responseSnapshot()); entity.setFailureCode(attempt.failureCode());
    entity.setStartedAt(toLocal(attempt.startedAt())); entity.setCompletedAt(attempt.completedAt() == null ? null : toLocal(attempt.completedAt())); entity.setVersion(attempt.version());
    entity.setQueryCount(0); entity.setNextQueryAt(attempt.status() == PaymentAttemptStatus.PROCESSING
        ? toLocal(attempt.startedAt().plusSeconds(300)) : null);
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

  public List<PaymentAttemptQueryClaim> claimQueryable(Instant now, int maxQueryCount, int limit, long lockSeconds) {
    return mapper.findQueryable(toLocal(now), maxQueryCount, limit).stream().map(entity -> {
      String claimToken = UUID.randomUUID().toString();
      boolean claimed = mapper.claimForQuery(entity.getAttemptId(), queryWorkerId, claimToken, toLocal(now),
          toLocal(now.plusSeconds(lockSeconds)), maxQueryCount) == 1;
      return claimed ? new PaymentAttemptQueryClaim(toDomain(entity), entity.getQueryCount(), claimToken) : null;
    }).filter(java.util.Objects::nonNull).toList();
  }

  public boolean completeQuery(String attemptId, String claimToken, Instant now, Instant nextQueryAt) {
    return mapper.completeQuery(attemptId, claimToken, toLocal(now), toLocal(nextQueryAt)) == 1;
  }

  public boolean releaseQueryClaim(String attemptId, String claimToken, Instant now, Instant nextQueryAt) {
    return mapper.releaseQueryClaim(attemptId, claimToken, toLocal(now), toLocal(nextQueryAt)) == 1;
  }

  public boolean update(PaymentAttemptStatus expected, PaymentAttempt attempt) {
    return update(expected, 0L, attempt);
  }

  public boolean update(PaymentAttemptStatus expected, long expectedVersion, PaymentAttempt attempt) {
    return mapper.updateAttempt(attempt.attemptId(), expected.name(), expectedVersion, attempt.status().name(),
        attempt.responseSnapshot(), attempt.failureCode(), attempt.completedAt() == null ? null : toLocal(attempt.completedAt())) == 1;
  }

  private PaymentAttempt toDomain(PaymentAttemptEntity e) {
    return new PaymentAttempt(e.getAttemptId(), e.getOrderId(), e.getChannelId(), e.getChannelRequestNo(), e.getAttemptNo(),
        PaymentAttemptStatus.valueOf(e.getStatus()), e.getRequestSummary(), e.getResponseSummary(), e.getFailureCode(),
        toInstant(e.getStartedAt()), e.getCompletedAt() == null ? null : toInstant(e.getCompletedAt()), e.getVersion());
  }
  public record PaymentAttemptQueryClaim(PaymentAttempt attempt, int queryCount, String claimToken) {}

  private static LocalDateTime toLocal(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
  private static Instant toInstant(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC); }
}
