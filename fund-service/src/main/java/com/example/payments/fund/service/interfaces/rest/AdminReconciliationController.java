package com.example.payments.fund.service.interfaces.rest;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/reconciliation")
public class AdminReconciliationController {
  private final JdbcClient jdbc;
  private final AdminRequestAuthorizer auth;

  public AdminReconciliationController(JdbcClient jdbc, AdminRequestAuthorizer auth) {
    this.jdbc = jdbc;
    this.auth = auth;
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
    jdbc.sql(
            "INSERT INTO settlement_bill (bill_id, channel_id, bill_date, currency, total_amount,"
                + " total_count, status, imported_at) VALUES"
                + " (:id,:channel,:date,:currency,:amount,:count,'IMPORTED',:now) ON DUPLICATE KEY"
                + " UPDATE total_amount=VALUES(total_amount), total_count=VALUES(total_count),"
                + " status='IMPORTED'")
        .param("id", id)
        .param("channel", request.channelId())
        .param("date", Date.valueOf(request.billDate()))
        .param("currency", request.currency())
        .param("amount", request.totalAmount())
        .param("count", request.totalCount())
        .param("now", java.sql.Timestamp.from(Instant.now()))
        .update();
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
        jdbc.sql(
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
        jdbc.sql("SELECT currency,total_amount,total_count FROM settlement_bill WHERE bill_id=:id")
            .param("id", billId)
            .query()
            .single();
    var actual =
        jdbc.sql(
                "SELECT COALESCE(SUM(CASE WHEN entry_type='PAYMENT_SUCCESS' AND"
                    + " debit_credit='CREDIT' THEN amount WHEN entry_type='REFUND_REVERSAL' AND"
                    + " debit_credit='DEBIT' THEN -amount ELSE 0 END),0) total, COUNT(DISTINCT"
                    + " order_id) count FROM ledger_entry WHERE currency=:currency")
            .param("currency", bill.get("currency"))
            .query()
            .single();
    var expected = (BigDecimal) bill.get("total_amount");
    var actualAmount = (BigDecimal) actual.get("total");
    if (expected.compareTo(actualAmount) != 0
        || ((Number) actual.get("count")).intValue()
            != ((Number) bill.get("total_count")).intValue()) {
      jdbc.sql(
              "INSERT INTO reconciliation_difference"
                  + " (difference_id,bill_id,difference_type,expected_amount,actual_amount,status,reason,created_at)"
                  + " VALUES"
                  + " (:id,:bill,'AMOUNT_MISMATCH',:expected,:actual,'OPEN','自动对账金额或笔数不一致',:now)")
          .param("id", "diff-" + UUID.randomUUID())
          .param("bill", billId)
          .param("expected", expected)
          .param("actual", actualAmount)
          .param("now", java.sql.Timestamp.from(Instant.now()))
          .update();
      jdbc.sql("UPDATE settlement_bill SET status='DIFFERENCE' WHERE bill_id=:id")
          .param("id", billId)
          .update();
      return Map.of(
          "billId", billId, "status", "DIFFERENCE", "expected", expected, "actual", actualAmount);
    }
    jdbc.sql("UPDATE settlement_bill SET status='MATCHED' WHERE bill_id=:id")
        .param("id", billId)
        .update();
    return Map.of("billId", billId, "status", "MATCHED");
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
        jdbc.sql(
                "UPDATE reconciliation_difference SET status='RESOLVED', reason=:reason,"
                    + " resolved_by=:operator, resolved_at=:now WHERE difference_id=:id AND"
                    + " status='OPEN'")
            .param("reason", request.reason())
            .param("operator", user)
            .param("now", java.sql.Timestamp.from(Instant.now()))
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
      int totalCount) {}

  public record ResolveRequest(String reason) {}
}
