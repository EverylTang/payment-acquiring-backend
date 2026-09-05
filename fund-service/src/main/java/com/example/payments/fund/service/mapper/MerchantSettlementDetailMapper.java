package com.example.payments.fund.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.payments.fund.service.model.MerchantSettlementDetailEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantSettlementDetailMapper extends BaseMapper<MerchantSettlementDetailEntity> {
  @Update(
      """
      UPDATE merchant_settlement_detail
      SET status = 'PROCESSING', settlement_batch_id = #{batchId}, updated_at = #{updatedAt}
      WHERE detail_id = #{detailId} AND status = 'PENDING' AND settlement_batch_id IS NULL
        AND expected_settlement_date <= #{settlementDate}
      """)
  int claimForSettlement(
      @Param("detailId") String detailId,
      @Param("batchId") String batchId,
      @Param("settlementDate") java.time.LocalDate settlementDate,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Update(
      """
      UPDATE merchant_settlement_detail
      SET status = 'SETTLED', actual_settlement_date = #{settlementDate},
          fund_account_entry_id = #{transactionId}, updated_at = #{updatedAt}
      WHERE detail_id = #{detailId} AND status = 'PROCESSING' AND settlement_batch_id = #{batchId}
      """)
  int markSettled(
      @Param("detailId") String detailId,
      @Param("batchId") String batchId,
      @Param("settlementDate") java.time.LocalDate settlementDate,
      @Param("transactionId") Long transactionId,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Update(
      """
      UPDATE merchant_settlement_detail
      SET status = 'PENDING', settlement_batch_id = NULL, remark = #{error}, updated_at = #{updatedAt}
      WHERE detail_id = #{detailId} AND status = 'PROCESSING' AND settlement_batch_id = #{batchId}
      """)
  int releaseFailedClaim(
      @Param("detailId") String detailId,
      @Param("batchId") String batchId,
      @Param("error") String error,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Update(
      """
      UPDATE merchant_settlement_detail
      SET refunded_amount = refunded_amount + #{amount},
          settlement_amount = GREATEST(settlement_amount - #{amount}, 0),
          status = CASE WHEN settlement_amount <= #{amount} THEN 'CANCELLED' ELSE 'PENDING' END,
          remark = #{remark}, updated_at = #{updatedAt}
      WHERE detail_id = #{detailId} AND status = 'PENDING'
        AND refunded_amount + #{amount} <= order_amount
      """)
  int applyPendingRefund(
      @Param("detailId") String detailId,
      @Param("amount") java.math.BigDecimal amount,
      @Param("remark") String remark,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);

  @Update(
      """
      UPDATE merchant_settlement_detail
      SET refunded_amount = refunded_amount + #{amount},
          settlement_amount = GREATEST(settlement_amount - #{amount}, 0),
          remark = #{remark}, updated_at = #{updatedAt}
      WHERE detail_id = #{detailId} AND status = 'SETTLED'
        AND refunded_amount + #{amount} <= order_amount
      """)
  int applySettledRefund(
      @Param("detailId") String detailId,
      @Param("amount") java.math.BigDecimal amount,
      @Param("remark") String remark,
      @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
