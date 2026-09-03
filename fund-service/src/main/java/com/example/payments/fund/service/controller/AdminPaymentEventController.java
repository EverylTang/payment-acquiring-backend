package com.example.payments.fund.service.controller;

import com.example.payments.fund.service.service.PaymentEventReplayAdminService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/payment-events")
@RequiredArgsConstructor
public class AdminPaymentEventController {
  private final PaymentEventReplayAdminService service;
  private final AdminRequestAuthorizer authorizer;

  @GetMapping("/failed")
  public Map<String, Object> failed(
      @RequestParam(defaultValue = "50") int limit,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "payment-event:list");
    return Map.of("items", service.findFailed(limit));
  }

  @GetMapping("/{id}")
  public Object get(
      @PathVariable long id,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "payment-event:detail");
    return service.find(id);
  }

  @PostMapping("/{id}/replay")
  public Object replay(
      @PathVariable long id,
      @RequestBody ReplayRequest request,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions,
      @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
    authorizer.authorize(token, operator, permissions, "payment-event:replay");
    return service.replay(id, operator, request.reason(), requestId);
  }

  public record ReplayRequest(String reason) {}
}
