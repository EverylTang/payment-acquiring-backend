package com.example.payments.trade.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentCallbackRecordMapper extends BaseMapper<PaymentCallbackRecordEntity> {
  PaymentCallbackRecordEntity findByCallbackId(@Param("callbackId") String callbackId);
}
