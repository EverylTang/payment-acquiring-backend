package com.example.payments.trade.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentAttemptMapper extends BaseMapper<PaymentAttemptEntity> {
  int countByOrderId(@Param("orderId") String orderId);

  java.util.List<PaymentAttemptEntity> findProcessingBefore(@Param("before") java.time.LocalDateTime before);

  PaymentAttemptEntity findByChannelOrderId(@Param("channelOrderId") String channelOrderId);

  int updateAttempt(@Param("attemptId") String attemptId, @Param("expected") String expected,
      @Param("next") String next, @Param("responseSummary") String responseSummary,
      @Param("failureCode") String failureCode, @Param("completedAt") java.time.LocalDateTime completedAt);
}
