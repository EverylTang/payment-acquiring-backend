package com.example.payments.fund.service.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReconciliationMapper {
  int upsertBill(
      @Param("id") String id,
      @Param("channel") String channel,
      @Param("date") String date,
      @Param("currency") String currency,
      @Param("amount") BigDecimal amount,
      @Param("count") int count,
      @Param("now") Instant now);

  int deleteBillLines(@Param("bill") String billId);

  int insertBillLine(
      @Param("bill") String billId,
      @Param("channelOrder") String channelOrder,
      @Param("merchant") String merchant,
      @Param("order") String order,
      @Param("type") String type,
      @Param("status") String status,
      @Param("amount") BigDecimal amount,
      @Param("currency") String currency);

  List<Map<String, Object>> selectOpenDifferences();

  BillRow selectBill(@Param("id") String billId);

  List<Map<String, Object>> selectBillLines(@Param("bill") String billId);

  List<Map<String, Object>> selectLedgerEntries(
      @Param("order") String orderId, @Param("entryType") String entryType);

  List<Map<String, Object>> selectPlatformOnlyEntries(
      @Param("currency") String currency,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      @Param("bill") String billId);

  int updateBillStatus(@Param("status") String status, @Param("id") String billId);

  int upsertDifference(
      @Param("id") String id,
      @Param("bill") String billId,
      @Param("type") String type,
      @Param("order") String orderId,
      @Param("expected") BigDecimal expected,
      @Param("actual") BigDecimal actual,
      @Param("reason") String reason,
      @Param("now") Instant now);

  int resolveDifference(
      @Param("reason") String reason,
      @Param("operator") String operator,
      @Param("now") Instant now,
      @Param("id") String differenceId);

  record BillRow(String currency, BigDecimal totalAmount, int totalCount, LocalDate billDate) {}
}
