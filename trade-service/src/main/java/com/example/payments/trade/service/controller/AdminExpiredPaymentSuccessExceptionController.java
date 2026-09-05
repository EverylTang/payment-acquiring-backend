package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.ExpiredPaymentSuccessExceptionService;
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
@RequestMapping("/api/admin/v1/reconciliation/expired-payment-successes")
@RequiredArgsConstructor
public class AdminExpiredPaymentSuccessExceptionController {
  private final ExpiredPaymentSuccessExceptionService service;
  private final AdminRequestAuthorizer authorizer;

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "OPEN") String status,
      @RequestParam(defaultValue = "50") int limit,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "reconciliation:difference:list");
    return Map.of("items", service.list(status, limit));
  }

  @PostMapping("/{exceptionId}/resolve")
  public Object resolve(
      @PathVariable String exceptionId,
      @RequestBody ResolutionRequest request,
      @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(gatewayToken, operator, permissions, "reconciliation:difference:resolve");
    return service.resolve(exceptionId, operator, request.resolution());
  }

  public record ResolutionRequest(String resolution) {}
}
