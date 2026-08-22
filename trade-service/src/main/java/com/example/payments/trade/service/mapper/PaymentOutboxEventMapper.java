package com.example.payments.trade.service.mapper;
import com.example.payments.trade.service.model.*;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentOutboxEventMapper extends BaseMapper<PaymentOutboxEventEntity> {
  List<PaymentOutboxEventEntity> findPending(@Param("now") LocalDateTime now, @Param("limit") int limit);
  int recoverExpiredClaims(@Param("now") LocalDateTime now);
  int claim(@Param("eventId") String eventId, @Param("workerId") String workerId,
      @Param("claimToken") String claimToken, @Param("now") LocalDateTime now,
      @Param("lockUntil") LocalDateTime lockUntil);
  int markPublished(@Param("eventId") String eventId, @Param("claimToken") String claimToken,
      @Param("now") LocalDateTime now, @Param("publishedAt") LocalDateTime publishedAt);
  int markFailed(@Param("eventId") String eventId, @Param("claimToken") String claimToken,
      @Param("now") LocalDateTime now, @Param("nextRetryAt") LocalDateTime nextRetryAt,
      @Param("lastError") String lastError, @Param("failureType") String failureType,
      @Param("failedAt") LocalDateTime failedAt, @Param("maxAttempts") int maxAttempts);

  PaymentOutboxEventEntity findByEventId(@Param("eventId") String eventId);

  java.util.List<PaymentOutboxEventEntity> findDead(@Param("limit") int limit);

  int redrive(@Param("eventId") String eventId, @Param("now") LocalDateTime now);

  int insertAudit(@Param("eventId") String eventId, @Param("operator") String operator,
      @Param("reason") String reason, @Param("fromStatus") String fromStatus,
      @Param("toStatus") String toStatus, @Param("requestId") String requestId,
      @Param("createdAt") LocalDateTime createdAt);
}
