package com.example.payments.fund.service.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LedgerEntryMapper extends BaseMapper<LedgerEntryEntity> {
  LedgerEntryEntity findByIdempotency(String key);

  BigDecimal sumRefundReversals(String orderId);

  BigDecimal originalPaymentAmount(String orderId);
}
