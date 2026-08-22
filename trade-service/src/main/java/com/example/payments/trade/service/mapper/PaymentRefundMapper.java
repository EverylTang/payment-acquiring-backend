package com.example.payments.trade.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.trade.service.model.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentRefundMapper extends BaseMapper<PaymentRefundEntity> {
  int claimForExecution(
      @Param("refundId") String refundId,
      @Param("owner") String owner,
      @Param("now") java.time.LocalDateTime now,
      @Param("until") java.time.LocalDateTime until);

  java.math.BigDecimal refundedAmount(String orderId);

  String lockOrder(String orderId);
}
