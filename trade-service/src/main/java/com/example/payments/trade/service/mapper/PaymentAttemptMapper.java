package com.example.payments.trade.service.mapper;
import com.example.payments.trade.service.model.*;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentAttemptMapper extends BaseMapper<PaymentAttemptEntity> {
  String findSuccessfulChannelOrder(@Param("orderId") String orderId);

  int countByOrderId(@Param("orderId") String orderId);

  java.util.List<PaymentAttemptEntity> findQueryable(
      @Param("now") java.time.LocalDateTime now,
      @Param("maxQueryCount") int maxQueryCount,
      @Param("limit") int limit);

  int claimForQuery(
      @Param("attemptId") String attemptId,
      @Param("owner") String owner,
      @Param("claimToken") String claimToken,
      @Param("now") java.time.LocalDateTime now,
      @Param("lockUntil") java.time.LocalDateTime lockUntil,
      @Param("maxQueryCount") int maxQueryCount);

  int completeQuery(
      @Param("attemptId") String attemptId,
      @Param("claimToken") String claimToken,
      @Param("now") java.time.LocalDateTime now,
      @Param("nextQueryAt") java.time.LocalDateTime nextQueryAt);

  int releaseQueryClaim(
      @Param("attemptId") String attemptId,
      @Param("claimToken") String claimToken,
      @Param("now") java.time.LocalDateTime now,
      @Param("nextQueryAt") java.time.LocalDateTime nextQueryAt);

  PaymentAttemptEntity findByChannelOrderId(@Param("channelOrderId") String channelOrderId);

  int updateAttempt(
      @Param("attemptId") String attemptId,
      @Param("expected") String expected,
      @Param("expectedVersion") Long expectedVersion,
      @Param("next") String next,
      @Param("responseSummary") String responseSummary,
      @Param("failureCode") String failureCode,
      @Param("completedAt") java.time.LocalDateTime completedAt);
}
