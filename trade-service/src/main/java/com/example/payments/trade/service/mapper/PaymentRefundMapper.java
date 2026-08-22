package com.example.payments.trade.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentRefundMapper extends BaseMapper<PaymentRefundEntity> {
  @Update("UPDATE payment_refund SET status='PROCESSING', attempt_count=attempt_count+1, processing_owner=#{owner}, processing_until=#{until}, updated_at=#{now} WHERE refund_id=#{refundId} AND ((status IN ('CREATED','FAILED') AND next_attempt_at <= #{now}) OR (status='PROCESSING' AND processing_until < #{now}))")
  int claimForExecution(@Param("refundId") String refundId, @Param("owner") String owner, @Param("now") java.time.LocalDateTime now, @Param("until") java.time.LocalDateTime until);
  @Select(
      "SELECT COALESCE(SUM(amount), 0) FROM payment_refund WHERE order_id = #{orderId} AND status"
          + " IN ('CREATED', 'PROCESSING', 'SUCCESS')")
  java.math.BigDecimal refundedAmount(String orderId);

  @Select("SELECT order_id FROM payment_order WHERE order_id = #{orderId} FOR UPDATE")
  String lockOrder(String orderId);
}
