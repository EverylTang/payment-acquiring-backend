package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentEventConsumptionMapper extends BaseMapper<PaymentEventConsumptionEntity> {
  PaymentEventConsumptionEntity findByEvent(@Param("eventId") String eventId, @Param("eventType") String eventType);

  int claim(@Param("id") Long id, @Param("owner") String owner, @Param("now") java.time.LocalDateTime now,
      @Param("until") java.time.LocalDateTime until);

  int markFailed(@Param("id") Long id, @Param("owner") String owner, @Param("failureType") String failureType,
      @Param("error") String error, @Param("now") java.time.LocalDateTime now);

  java.util.List<PaymentEventConsumptionEntity> findFailed(@Param("limit") int limit);

  int requestReplay(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

  int replayFailed(@Param("id") Long id, @Param("error") String error, @Param("now") java.time.LocalDateTime now);

  int insertReplayAudit(@Param("eventId") String eventId, @Param("operator") String operator,
      @Param("reason") String reason, @Param("requestId") String requestId,
      @Param("createdAt") java.time.LocalDateTime createdAt);
}
