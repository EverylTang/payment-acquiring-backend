package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.fund.service.model.MerchantFundTransactionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MerchantFundTransactionMapper extends BaseMapper<MerchantFundTransactionEntity> {
  @Select(
      "SELECT * FROM merchant_fund_transaction WHERE idempotency_key = #{idempotencyKey} LIMIT 1")
  MerchantFundTransactionEntity findByIdempotency(@Param("idempotencyKey") String idempotencyKey);

  @Select(
      """
      <script>
      SELECT * FROM merchant_fund_transaction
      <where>
        <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
        <if test="currency != null and currency != ''">AND currency = #{currency}</if>
        <if test="transactionType != null and transactionType != ''">AND transaction_type = #{transactionType}</if>
        <if test="createdFrom != null">AND created_at &gt;= #{createdFrom}</if>
        <if test="createdToExclusive != null">AND created_at &lt; #{createdToExclusive}</if>
      </where>
      ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
      </script>
      """)
  List<MerchantFundTransactionEntity> selectAdminPage(
      @Param("merchantId") String merchantId,
      @Param("currency") String currency,
      @Param("transactionType") String transactionType,
      @Param("createdFrom") LocalDateTime createdFrom,
      @Param("createdToExclusive") LocalDateTime createdToExclusive,
      @Param("limit") int limit,
      @Param("offset") int offset);

  @Select(
      """
      <script>
      SELECT COUNT(*) FROM merchant_fund_transaction
      <where>
        <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
        <if test="currency != null and currency != ''">AND currency = #{currency}</if>
        <if test="transactionType != null and transactionType != ''">AND transaction_type = #{transactionType}</if>
        <if test="createdFrom != null">AND created_at &gt;= #{createdFrom}</if>
        <if test="createdToExclusive != null">AND created_at &lt; #{createdToExclusive}</if>
      </where>
      </script>
      """)
  long countAdminPage(
      @Param("merchantId") String merchantId,
      @Param("currency") String currency,
      @Param("transactionType") String transactionType,
      @Param("createdFrom") LocalDateTime createdFrom,
      @Param("createdToExclusive") LocalDateTime createdToExclusive);
}
