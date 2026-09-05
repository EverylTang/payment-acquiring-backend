package com.example.payments.trade.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.model.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderEntity> {
  PaymentOrderEntity findByMerchantOrder(
      @Param("merchantId") String merchantId,
      @Param("merchantOrderNo") String merchantOrderNo,
      @Param("orderType") String orderType);

  PaymentOrderEntity findByIdempotency(
      @Param("merchantId") String merchantId,
      @Param("key") String key,
      @Param("orderType") String orderType);

  int updateStatus(
      @Param("orderId") String orderId,
      @Param("expected") String expected,
      @Param("next") String next,
      @Param("paidAt") LocalDateTime paidAt);

  List<PaymentOrderEntity> findExpirable(
      @Param("now") LocalDateTime now, @Param("limit") int limit);

  int expire(
      @Param("orderId") String orderId,
      @Param("expected") String expected,
      @Param("now") LocalDateTime now);

  int updateCallbackState(
      @Param("orderId") String orderId,
      @Param("callbackStatus") String callbackStatus,
      @Param("callbackEventId") String callbackEventId,
      @Param("callbackAttemptCount") int callbackAttemptCount,
      @Param("callbackLastNotifiedAt") LocalDateTime callbackLastNotifiedAt,
      @Param("callbackLastError") String callbackLastError);

  Map<String, Object> aggregateStatistics();

  default int updateStatus(
      String orderId, OrderStatus expected, OrderStatus next, LocalDateTime paidAt) {
    return updateStatus(orderId, expected.name(), next.name(), paidAt);
  }
}
