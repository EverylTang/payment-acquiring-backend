package com.example.payments.fund.service.controller;

import com.example.payments.fund.service.service.ReconciliationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/reconciliation")
@RequiredArgsConstructor
public class AdminReconciliationController {
  private final ReconciliationService service;
  private final AdminRequestAuthorizer auth;

  @PostMapping("/bills")
  public Map<String, Object> importBill(
      @RequestBody BillRequest r,
      @RequestHeader("X-Gateway-Token") String t,
      @RequestHeader("X-User-Id") String u,
      @RequestHeader("X-Permissions") String permissions) {
    var lines =
        r.lines() == null
            ? null
            : r.lines().stream()
                .map(
                    x ->
                        new ReconciliationService.BillLineRequest(
                            x.channelOrderId(),
                            x.merchantId(),
                            x.orderId(),
                            x.transactionType(),
                            x.status(),
                            x.amount(),
                            x.currency()))
                .toList();
    auth.authorize(t, u, permissions, "reconciliation:bill:import");
    return service.importBill(
        new ReconciliationService.BillRequest(
            r.billId(),
            r.channelId(),
            r.billDate(),
            r.currency(),
            r.totalAmount(),
            r.totalCount(),
            lines));
  }

  @GetMapping("/differences")
  public Map<String, Object> differences(
      @RequestHeader("X-Gateway-Token") String t,
      @RequestHeader("X-User-Id") String u,
      @RequestHeader("X-Permissions") String permissions) {
    auth.authorize(t, u, permissions, "reconciliation:difference:list");
    return service.differences();
  }

  @PostMapping("/bills/{billId}/reconcile")
  public Map<String, Object> reconcile(
      @PathVariable String billId,
      @RequestHeader("X-Gateway-Token") String t,
      @RequestHeader("X-User-Id") String u,
      @RequestHeader("X-Permissions") String permissions) {
    auth.authorize(t, u, permissions, "reconciliation:bill:reconcile");
    return service.reconcile(billId);
  }

  @PostMapping("/differences/{differenceId}/resolve")
  public Map<String, Object> resolve(
      @PathVariable String differenceId,
      @RequestBody ResolveRequest r,
      @RequestHeader("X-Gateway-Token") String t,
      @RequestHeader("X-User-Id") String u,
      @RequestHeader("X-Permissions") String permissions) {
    auth.authorize(t, u, permissions, "reconciliation:difference:resolve");
    return service.resolve(differenceId, new ReconciliationService.ResolveRequest(r.reason()), u);
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
