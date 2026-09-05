package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.fund.service.model.MerchantFundTransactionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MerchantFundTransactionMapper extends BaseMapper<MerchantFundTransactionEntity> {
  @Select(
      "SELECT * FROM merchant_fund_transaction WHERE idempotency_key = #{idempotencyKey} LIMIT 1")
  MerchantFundTransactionEntity findByIdempotency(@Param("idempotencyKey") String idempotencyKey);
}
