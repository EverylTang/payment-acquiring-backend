package com.example.payments.fund.service.controller;

import com.example.payments.fund.service.model.MerchantSettlementRuleEntity;
import com.example.payments.fund.service.service.MerchantSettlementService;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/settlement")
@RequiredArgsConstructor
public class AdminSettlementController {
  private final MerchantSettlementService service;
  private final AdminRequestAuthorizer authorizer;

  @GetMapping("/rules")
  public Map<String, Object> rules(
      @RequestParam(required = false) String merchantId,
      @RequestParam(required = false) String currency,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "settlement:rule:list");
    return Map.of("items", service.listRules(merchantId, currency));
  }

  @PostMapping("/rules")
  public MerchantSettlementRuleEntity saveRule(
      @RequestBody MerchantSettlementRuleEntity rule,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "settlement:rule:manage");
    return service.saveRule(rule);
  }

  @PatchMapping("/rules/{id}/status")
  public MerchantSettlementRuleEntity status(
      @PathVariable long id,
      @RequestBody StatusRequest request,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "settlement:rule:manage");
    return service.changeRuleStatus(id, request.status());
  }

  @PostMapping("/batches")
  public Map<String, Object> runBatch(
      @RequestBody BatchRequest request,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "settlement:batch:run");
    LocalDate date = request.settlementDate() == null ? LocalDate.now() : request.settlementDate();
    return Map.of("batchId", service.generateSettlementBatch(date, false));
  }

  @GetMapping("/batches/{batchId}")
  public Map<String, Object> batch(
      @PathVariable String batchId,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "settlement:batch:read");
    return Map.of(
        "batch",
        service.getBatchInfo(batchId),
        "details",
        service.getSettlementDetailsByBatch(batchId));
  }

  public record BatchRequest(LocalDate settlementDate) {}
  public record StatusRequest(String status) {}
}
