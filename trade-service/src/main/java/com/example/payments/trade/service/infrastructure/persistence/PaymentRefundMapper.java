package com.example.payments.trade.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentRefundMapper extends BaseMapper<PaymentRefundEntity> {
  @Select(
      "SELECT COALESCE(SUM(amount), 0) FROM payment_refund WHERE order_id = #{orderId} AND status"
          + " IN ('CREATED', 'PROCESSING', 'SUCCESS')")
  java.math.BigDecimal refundedAmount(String orderId);

  @Select("SELECT order_id FROM payment_order WHERE order_id = #{orderId} FOR UPDATE")
  String lockOrder(String orderId);
}
