package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.fund.service.model.MerchantSettlementRuleEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MerchantSettlementRuleMapper extends BaseMapper<MerchantSettlementRuleEntity> {
  @Select(
      """
      <script>
      SELECT * FROM merchant_settlement_rule
      <where>
        <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
        <if test="productCode != null and productCode != ''">AND product_code = #{productCode}</if>
        <if test="currency != null and currency != ''">AND currency = #{currency}</if>
        <if test="status != null and status != ''">AND status = #{status}</if>
      </where>
      ORDER BY effective_date DESC, id DESC LIMIT #{limit} OFFSET #{offset}
      </script>
      """)
  List<MerchantSettlementRuleEntity> selectAdminPage(
      @Param("merchantId") String merchantId,
      @Param("productCode") String productCode,
      @Param("currency") String currency,
      @Param("status") String status,
      @Param("limit") int limit,
      @Param("offset") int offset);

  @Select(
      """
      <script>
      SELECT COUNT(*) FROM merchant_settlement_rule
      <where>
        <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
        <if test="productCode != null and productCode != ''">AND product_code = #{productCode}</if>
        <if test="currency != null and currency != ''">AND currency = #{currency}</if>
        <if test="status != null and status != ''">AND status = #{status}</if>
      </where>
      </script>
      """)
  long countAdminPage(
      @Param("merchantId") String merchantId,
      @Param("productCode") String productCode,
      @Param("currency") String currency,
      @Param("status") String status);
}
