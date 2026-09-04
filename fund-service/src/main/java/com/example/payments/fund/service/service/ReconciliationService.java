package com.example.payments.fund.service.service;

import com.example.payments.fund.service.mapper.ReconciliationMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReconciliationService {
  private final ReconciliationMapper mapper;
  private final MeterRegistry metrics;

  public Map<String, Object> importBill(BillRequest request) {
    var id =
        request.billId() == null || request.billId().isBlank()
            ? "bill-" + UUID.randomUUID()
            : request.billId();
    mapper.upsertBill(id, request.channelId(), request.billDate(), request.currency(), request.totalAmount(), request.totalCount(), Instant.now());
    mapper.deleteBillLines(id);
    if (request.lines() != null) {
      for (BillLineRequest line : request.lines()) {
        mapper.insertBillLine(id, line.channelOrderId(), line.merchantId(), line.orderId(), line.transactionType(), line.status(), line.amount(), line.currency());
      }
    }
    return Map.of("billId", id, "status", "IMPORTED");
  }

  public Map<String, Object> differences() {
    return Map.of(
        "items",
        mapper.selectOpenDifferences());
  }

  public Map<String, Object> reconcile(String billId) {
    var bill = mapper.selectBill(billId);
    if (bill == null) throw new IllegalArgumentException("账单不存在");
    var lines = mapper.selectBillLines(billId);
    if (lines.isEmpty()) throw new IllegalArgumentException("账单缺少逐笔明细");
    var differences = new ArrayList<Map<String, Object>>();
    for (var line : lines) {
      String orderId = (String) line.get("order_id");
      String type = String.valueOf(line.get("transaction_type"));
      String expectedCurrency = String.valueOf(line.get("currency"));
      var actualRows =
          orderId == null
              ? List.<Map<String, Object>>of()
              : mapper.selectLedgerEntries(orderId, "REFUND".equalsIgnoreCase(type) ? "REFUND_REVERSAL" : "PAYMENT_SUCCESS");
      String difference = null;
      BigDecimal actualAmount = null;
      if (actualRows.size() > 1) difference = "DUPLICATE";
      else if (actualRows.isEmpty()) difference = "CHANNEL_ONLY";
      else {
        var actual = actualRows.get(0);
        actualAmount = (BigDecimal) actual.get("amount");
        if (!expectedCurrency.equals(actual.get("currency"))) difference = "CURRENCY_MISMATCH";
        else if (((BigDecimal) line.get("amount")).compareTo(actualAmount) != 0)
          difference = "AMOUNT_MISMATCH";
        else if (line.get("status") != null
            && !String.valueOf(line.get("status")).equalsIgnoreCase("SUCCESS"))
          difference = "STATUS_MISMATCH";
      }
      if (difference != null) {
        metrics.counter("reconciliation.difference.open", "type", difference).increment();
        String key = String.valueOf(line.get("channel_order_id"));
        recordDifference(
            billId,
            difference,
            orderId,
            (BigDecimal) line.get("amount"),
            actualAmount,
            "逐笔账单与平台账本不一致: " + key);
        differences.add(Map.of("orderId", key, "differenceType", difference));
      }
    }
    // 平台有账本但渠道账单没有对应逐笔记录。
    var date = bill.billDate();
    var platformOnly =
        mapper.selectPlatformOnlyEntries(bill.currency(), date, date.plusDays(1), billId);
    for (var row : platformOnly) {
      recordDifference(
          billId,
          "PLATFORM_ONLY",
          (String) row.get("order_id"),
          null,
          (BigDecimal) row.get("amount"),
          "平台账本缺少渠道逐笔记录");
      differences.add(
          Map.of(
              "orderId", String.valueOf(row.get("order_id")), "differenceType", "PLATFORM_ONLY"));
    }
    String status = differences.isEmpty() ? "MATCHED" : "DIFFERENCE";
    mapper.updateBillStatus(status, billId);
    return Map.of(
        "billId",
        billId,
        "status",
        status,
        "differenceCount",
        differences.size(),
        "differences",
        differences);
  }

  private void recordDifference(
      String billId,
      String type,
      String orderId,
      BigDecimal expected,
      BigDecimal actual,
      String reason) {
    var key = "diff-" + billId + "-" + type + "-" + (orderId == null ? "unknown" : orderId);
    mapper.upsertDifference(key, billId, type, orderId, expected, actual, reason, Instant.now());
  }

  public Map<String, Object> resolve(String differenceId, ResolveRequest request, String operator) {
    var updated = mapper.resolveDifference(request.reason(), operator, Instant.now(), differenceId);
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

  public record BillLineRequest(
      String channelOrderId,
      String merchantId,
      String orderId,
      String transactionType,
      String status,
      BigDecimal amount,
      String currency) {}

  public record ResolveRequest(String reason) {}
}
