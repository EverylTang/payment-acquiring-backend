package com.example.payments.fund.service.controller;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import com.example.payments.fund.service.mapper.MybatisPlusClient;
import org.springframework.web.bind.annotation.*;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
@RequestMapping("/api/admin/v1/reconciliation")
public class AdminReconciliationController {
  private final MybatisPlusClient mybatisClient;
  private final AdminRequestAuthorizer auth;
  private final MeterRegistry metrics;

  public AdminReconciliationController(MybatisPlusClient mybatisClient, AdminRequestAuthorizer auth, MeterRegistry metrics) {
    this.mybatisClient = mybatisClient;
    this.auth = auth;
    this.metrics = metrics;
  }

  @PostMapping("/bills")
  public Map<String, Object> importBill(
      @RequestBody BillRequest request,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String user,
      @RequestHeader("X-Roles") String roles) {
    auth.authorize(token, user, roles);
    var id =
        request.billId() == null || request.billId().isBlank()
            ? "bill-" + UUID.randomUUID()
            : request.billId();
    mybatisClient.sql(
            "INSERT INTO settlement_bill (bill_id, channel_id, bill_date, currency, total_amount,"
                + " total_count, status, imported_at) VALUES"
                + " (:id,:channel,:date,:currency,:amount,:count,'IMPORTED',:now) ON DUPLICATE KEY"
                + " UPDATE total_amount=VALUES(total_amount), total_count=VALUES(total_count),"
                + " status='IMPORTED'")
        .param("id", id)
        .param("channel", request.channelId())
        .param("date", request.billDate())
        .param("currency", request.currency())
        .param("amount", request.totalAmount())
        .param("count", request.totalCount())
        .param("now", Instant.now())
        .update();
    mybatisClient.sql("DELETE FROM settlement_bill_line WHERE bill_id=:bill")
        .param("bill", id).update();
    if (request.lines() != null) {
      for (BillLineRequest line : request.lines()) {
        mybatisClient.sql("INSERT INTO settlement_bill_line (bill_id,channel_order_id,merchant_id,order_id,transaction_type,status,amount,currency) "
                + "VALUES (:bill,:channelOrder,:merchant,:order,:type,:status,:amount,:currency)")
            .param("bill", id).param("channelOrder", line.channelOrderId())
            .param("merchant", line.merchantId()).param("order", line.orderId())
            .param("type", line.transactionType()).param("status", line.status())
            .param("amount", line.amount()).param("currency", line.currency()).update();
      }
    }
    return Map.of("billId", id, "status", "IMPORTED");
  }

  @GetMapping("/differences")
  public Map<String, Object> differences(
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String user,
      @RequestHeader("X-Roles") String roles) {
    auth.authorize(token, user, roles);
    return Map.of(
        "items",
        mybatisClient.sql(
                "SELECT * FROM reconciliation_difference WHERE status='OPEN' ORDER BY created_at"
                    + " DESC LIMIT 200")
            .query()
            .listOfRows());
  }

  @PostMapping("/bills/{billId}/reconcile")
  public Map<String, Object> reconcile(
      @PathVariable String billId,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String user,
      @RequestHeader("X-Roles") String roles) {
    auth.authorize(token, user, roles);
    var bill =
        mybatisClient.sql("SELECT currency,total_amount,total_count,bill_date FROM settlement_bill WHERE bill_id=:id")
            .param("id", billId)
            .query((rs, row) -> {
              var result = new HashMap<String, Object>();
              result.put("currency", rs.getString("currency"));
              result.put("total_amount", rs.getBigDecimal("total_amount"));
              result.put("total_count", rs.getInt("total_count"));
              result.put("bill_date", rs.getDate("bill_date").toLocalDate());
              return result;
            })
            .single();
    var lines = mybatisClient.sql("SELECT * FROM settlement_bill_line WHERE bill_id=:bill")
        .param("bill", billId).query().listOfRows();
    if (lines.isEmpty()) throw new IllegalArgumentException("账单缺少逐笔明细");
    var differences = new ArrayList<Map<String, Object>>();
    for (var line : lines) {
      String orderId = (String) line.get("order_id");
      String type = String.valueOf(line.get("transaction_type"));
      String expectedCurrency = String.valueOf(line.get("currency"));
      var actualRows = orderId == null ? List.<Map<String, Object>>of() : mybatisClient.sql(
          "SELECT * FROM ledger_entry WHERE order_id=:order AND entry_type=:entryType")
          .param("order", orderId)
          .param("entryType", "REFUND".equalsIgnoreCase(type) ? "REFUND_REVERSAL" : "PAYMENT_SUCCESS")
          .query().listOfRows();
      String difference = null;
      BigDecimal actualAmount = null;
      if (actualRows.size() > 1) difference = "DUPLICATE";
      else if (actualRows.isEmpty()) difference = "CHANNEL_ONLY";
      else {
        var actual = actualRows.get(0);
        actualAmount = (BigDecimal) actual.get("amount");
        if (!expectedCurrency.equals(actual.get("currency"))) difference = "CURRENCY_MISMATCH";
        else if (((BigDecimal) line.get("amount")).compareTo(actualAmount) != 0) difference = "AMOUNT_MISMATCH";
        else if (line.get("status") != null && !String.valueOf(line.get("status")).equalsIgnoreCase("SUCCESS")) difference = "STATUS_MISMATCH";
      }
      if (difference != null) {
        metrics.counter("reconciliation.difference.open", "type", difference).increment();
        String key = String.valueOf(line.get("channel_order_id"));
        recordDifference(billId, difference, orderId, (BigDecimal) line.get("amount"), actualAmount,
            "逐笔账单与平台账本不一致: " + key);
        differences.add(Map.of("orderId", key, "differenceType", difference));
      }
    }
    // 平台有账本但渠道账单没有对应逐笔记录。
    var date = (LocalDate) bill.get("bill_date");
    var platformOnly = mybatisClient.sql("SELECT order_id,amount FROM ledger_entry WHERE currency=:currency AND created_at >= :start AND created_at < :end AND entry_type IN ('PAYMENT_SUCCESS','REFUND_REVERSAL') AND order_id NOT IN (SELECT order_id FROM settlement_bill_line WHERE bill_id=:bill AND order_id IS NOT NULL)")
        .param("currency", bill.get("currency")).param("start", date)
        .param("end", date.plusDays(1)).param("bill", billId).query().listOfRows();
    for (var row : platformOnly) {
      recordDifference(billId, "PLATFORM_ONLY", (String) row.get("order_id"), null, (BigDecimal) row.get("amount"), "平台账本缺少渠道逐笔记录");
      differences.add(Map.of("orderId", String.valueOf(row.get("order_id")), "differenceType", "PLATFORM_ONLY"));
    }
    String status = differences.isEmpty() ? "MATCHED" : "DIFFERENCE";
    mybatisClient.sql("UPDATE settlement_bill SET status=:status WHERE bill_id=:id").param("status", status).param("id", billId).update();
    return Map.of("billId", billId, "status", status, "differenceCount", differences.size(), "differences", differences);
  }

  private void recordDifference(String billId, String type, String orderId, BigDecimal expected, BigDecimal actual, String reason) {
    var key = "diff-" + billId + "-" + type + "-" + (orderId == null ? "unknown" : orderId);
    mybatisClient.sql("INSERT INTO reconciliation_difference (difference_id,bill_id,difference_type,order_id,expected_amount,actual_amount,status,reason,created_at) VALUES (:id,:bill,:type,:order,:expected,:actual,'OPEN',:reason,:now) ON DUPLICATE KEY UPDATE expected_amount=VALUES(expected_amount),actual_amount=VALUES(actual_amount),reason=VALUES(reason),status=IF(status='RESOLVED',status,'OPEN')")
        .param("id", key).param("bill", billId).param("type", type).param("order", orderId)
        .param("expected", expected).param("actual", actual).param("reason", reason)
        .param("now", Instant.now()).update();
  }

  @PostMapping("/differences/{differenceId}/resolve")
  public Map<String, Object> resolve(
      @PathVariable String differenceId,
      @RequestBody ResolveRequest request,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String user,
      @RequestHeader("X-Roles") String roles) {
    auth.authorize(token, user, roles);
    var updated =
        mybatisClient.sql(
                "UPDATE reconciliation_difference SET status='RESOLVED', reason=:reason,"
                    + " resolved_by=:operator, resolved_at=:now WHERE difference_id=:id AND"
                    + " status='OPEN'")
            .param("reason", request.reason())
            .param("operator", user)
            .param("now", Instant.now())
            .param("id", differenceId)
            .update();
    if (updated != 1) throw new IllegalArgumentException("差异不存在或已处理");
    return Map.of("differenceId", differenceId, "status", "RESOLVED");
  }

  public record BillRequest(
      String billId,
      String channelId,
      String billDate,
      String currency,
      BigDecimal totalAmount,
      int totalCount,
      List<BillLineRequest> lines) {}

  public record BillLineRequest(String channelOrderId, String merchantId, String orderId,
      String transactionType, String status, BigDecimal amount, String currency) {}

  public record ResolveRequest(String reason) {}
}
