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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReconciliationService {
  private final ReconciliationMapper mapper;
  private final MeterRegistry metrics;

  @Transactional
  public Map<String, Object> importBill(BillRequest request) {
    var id =
        request.billId() == null || request.billId().isBlank()
            ? "bill-" + UUID.randomUUID()
            : request.billId();
    return saveBill(id, request);
  }

  @Transactional
  public Map<String, Object> updateBill(String billId, BillRequest request) {
    var existing = mapper.selectSettlementBill(billId);
    if (existing == null) throw new IllegalArgumentException("账单不存在");
    if ("MATCHED".equals(existing.status())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "已匹配账单不可编辑");
    }
    if (request.billId() != null
        && !request.billId().isBlank()
        && !billId.equals(request.billId())) {
      throw new IllegalArgumentException("账单 ID 不可修改");
    }
    return saveBill(billId, request);
  }

  private Map<String, Object> saveBill(String id, BillRequest request) {
    validateBill(request);
    mapper.upsertBill(
        id,
        request.channelId().trim(),
        request.billDate(),
        request.currency().trim().toUpperCase(java.util.Locale.ROOT),
        request.totalAmount(),
        request.totalCount(),
        Instant.now());
    mapper.deleteBillLines(id);
    for (BillLineRequest line : request.lines()) {
      mapper.insertBillLine(
          id,
          line.channelOrderId().trim(),
          line.merchantId(),
          line.orderId(),
          line.transactionType().trim().toUpperCase(java.util.Locale.ROOT),
          line.status().trim().toUpperCase(java.util.Locale.ROOT),
          line.amount(),
          line.currency().trim().toUpperCase(java.util.Locale.ROOT));
    }
    return Map.of("billId", id, "status", "IMPORTED");
  }

  private void validateBill(BillRequest request) {
    if (request.channelId() == null || request.channelId().isBlank()) {
      throw new IllegalArgumentException("渠道 ID 不能为空");
    }
    if (request.currency() == null || !request.currency().matches("[A-Za-z]{3}")) {
      throw new IllegalArgumentException("账单币种必须为三位字母代码");
    }
    try {
      LocalDate.parse(request.billDate());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("账期必须为 YYYY-MM-DD");
    }
    if (request.totalAmount() == null
        || request.totalAmount().signum() < 0
        || request.totalCount() < 0) {
      throw new IllegalArgumentException("账单金额或笔数无效");
    }
    if (request.lines() == null || request.lines().isEmpty()) {
      throw new IllegalArgumentException("账单必须包含逐笔明细");
    }
    if (request.totalCount() != request.lines().size()) {
      throw new IllegalArgumentException("账单笔数必须与逐笔明细数量一致");
    }
    var lineTotal = BigDecimal.ZERO;
    for (BillLineRequest line : request.lines()) {
      if (line == null
          || line.channelOrderId() == null
          || line.channelOrderId().isBlank()
          || line.transactionType() == null
          || line.transactionType().isBlank()
          || line.status() == null
          || line.status().isBlank()
          || line.amount() == null
          || line.amount().signum() < 0
          || line.currency() == null
          || !line.currency().matches("[A-Za-z]{3}")) {
        throw new IllegalArgumentException("逐笔明细字段无效");
      }
      if (!request.currency().equalsIgnoreCase(line.currency())) {
        throw new IllegalArgumentException("逐笔明细币种必须与账单币种一致");
      }
      lineTotal = lineTotal.add(line.amount());
    }
    if (lineTotal.compareTo(request.totalAmount()) != 0) {
      throw new IllegalArgumentException("账单总金额必须与逐笔明细金额之和一致");
    }
  }

  public Map<String, Object> differences() {
    return Map.of("items", mapper.selectOpenDifferences());
  }

  public Map<String, Object> bills(int page, int pageSize) {
    int currentPage = Math.max(page, 1);
    int size = Math.clamp(pageSize, 1, 100);
    int offset = (currentPage - 1) * size;
    var items =
        mapper.selectBills(offset, size).stream()
            .map(
                bill ->
                    Map.<String, Object>of(
                        "billId", bill.billId(),
                        "channelId", bill.channelId(),
                        "billDate", bill.billDate(),
                        "currency", bill.currency(),
                        "totalAmount", bill.totalAmount(),
                        "totalCount", bill.totalCount(),
                        "status", bill.status(),
                        "importedAt", bill.importedAt()))
            .toList();
    return Map.of(
        "items", items,
        "page", currentPage,
        "pageSize", size,
        "total", mapper.countBills());
  }

  public Map<String, Object> bill(String billId) {
    var bill = mapper.selectSettlementBill(billId);
    if (bill == null) throw new IllegalArgumentException("账单不存在");
    return Map.of(
        "billId", bill.billId(),
        "channelId", bill.channelId(),
        "currency", bill.currency(),
        "totalAmount", bill.totalAmount(),
        "totalCount", bill.totalCount(),
        "billDate", bill.billDate(),
        "status", bill.status(),
        "importedAt", bill.importedAt(),
        "lines", mapper.selectBillLines(billId));
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
              : mapper.selectLedgerEntries(
                  orderId, "REFUND".equalsIgnoreCase(type) ? "REFUND_REVERSAL" : "PAYMENT_SUCCESS");
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
