package com.example.payments.trade.service.mapper;
import com.example.payments.trade.service.model.*;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentCallbackRecordMapper extends BaseMapper<PaymentCallbackRecordEntity> {
  PaymentCallbackRecordEntity findByCallbackId(@Param("callbackId") String callbackId);
}
