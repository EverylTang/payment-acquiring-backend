package com.example.payments.trade.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentOutboxEventMapper extends BaseMapper<PaymentOutboxEventEntity> {
  List<PaymentOutboxEventEntity> findPending(@Param("now") LocalDateTime now, @Param("limit") int limit);
  int markPublished(@Param("eventId") String eventId, @Param("publishedAt") LocalDateTime publishedAt);
  int markFailed(@Param("eventId") String eventId, @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("lastError") String lastError);
}
