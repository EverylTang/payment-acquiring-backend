package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.fund.service.model.MerchantFundAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantFundAccountMapper extends BaseMapper<MerchantFundAccountEntity> {
  @Update(
      """
      UPDATE merchant_fund_account
      SET balance = balance + #{amount}, total_income = total_income + #{amount},
          version = version + 1, updated_at = #{updatedAt}
      WHERE account_id = #{accountId} AND status = 'ACTIVE' AND version = #{expectedVersion}
      """)
  int creditForSettlement(
      @Param("accountId") String accountId,
      @Param("amount") java.math.BigDecimal amount,
      @Param("expectedVersion") Integer expectedVersion,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Update(
      """
      UPDATE merchant_fund_account
      SET balance = balance - #{amount}, total_expense = total_expense + #{amount},
          version = version + 1, updated_at = #{updatedAt}
      WHERE account_id = #{accountId} AND status = 'ACTIVE' AND version = #{expectedVersion}
      """)
  int debitForRefund(
      @Param("accountId") String accountId,
      @Param("amount") java.math.BigDecimal amount,
      @Param("expectedVersion") Integer expectedVersion,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
