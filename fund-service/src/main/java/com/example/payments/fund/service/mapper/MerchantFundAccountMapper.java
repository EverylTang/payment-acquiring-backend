package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.fund.service.model.MerchantFundAccountEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantFundAccountMapper extends BaseMapper<MerchantFundAccountEntity> {
  @Select(
      """
      <script>
      SELECT * FROM merchant_fund_account
      <where>
        <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
        <if test="currency != null and currency != ''">AND currency = #{currency}</if>
        <if test="status != null and status != ''">AND status = #{status}</if>
      </where>
      ORDER BY updated_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
      </script>
      """)
  List<MerchantFundAccountEntity> selectAdminPage(
      @Param("merchantId") String merchantId,
      @Param("currency") String currency,
      @Param("status") String status,
      @Param("limit") int limit,
      @Param("offset") int offset);

  @Select(
      """
      <script>
      SELECT COUNT(*) FROM merchant_fund_account
      <where>
        <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
        <if test="currency != null and currency != ''">AND currency = #{currency}</if>
        <if test="status != null and status != ''">AND status = #{status}</if>
      </where>
      </script>
      """)
  long countAdminPage(
      @Param("merchantId") String merchantId,
      @Param("currency") String currency,
      @Param("status") String status);

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
