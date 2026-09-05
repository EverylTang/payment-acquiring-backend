package com.example.payments.trade.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.trade.service.model.ExpiredPaymentSuccessExceptionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExpiredPaymentSuccessExceptionMapper
    extends BaseMapper<ExpiredPaymentSuccessExceptionEntity> {}
