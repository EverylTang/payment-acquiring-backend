package com.example.payments.trade.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.trade.service.domain.OrderStatus;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderEntity> {
  PaymentOrderEntity findByMerchantOrder(String merchantId, String merchantOrderNo);

  PaymentOrderEntity findByIdempotency(String merchantId, String key);

  int updateStatus(String orderId, String expected, String next, LocalDateTime paidAt);

  default int updateStatus(String orderId, OrderStatus expected, OrderStatus next, LocalDateTime paidAt) {
    return updateStatus(orderId, expected.name(), next.name(), paidAt);
  }
}
