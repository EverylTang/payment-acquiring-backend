package com.example.payments.trade.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.model.*;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderEntity> {
  PaymentOrderEntity findByMerchantOrder(
      @Param("merchantId") String merchantId, @Param("merchantOrderNo") String merchantOrderNo);

  PaymentOrderEntity findByIdempotency(
      @Param("merchantId") String merchantId, @Param("key") String key);

  int updateStatus(
      @Param("orderId") String orderId,
      @Param("expected") String expected,
      @Param("next") String next,
      @Param("paidAt") LocalDateTime paidAt);

  default int updateStatus(
      String orderId, OrderStatus expected, OrderStatus next, LocalDateTime paidAt) {
    return updateStatus(orderId, expected.name(), next.name(), paidAt);
  }
}
