package com.example.payments.trade.service.controller;

import com.example.payments.trade.service.service.PaymentOutboxAdminService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/outbox")
public class AdminOutboxController {
  private final PaymentOutboxAdminService service;
  private final AdminRequestAuthorizer authorizer;

  public AdminOutboxController(PaymentOutboxAdminService service, AdminRequestAuthorizer authorizer) {
    this.service = service;
    this.authorizer = authorizer;
  }

  @GetMapping("/dead")
  public Map<String, Object> dead(@RequestParam(defaultValue = "50") int limit,
      @RequestHeader("X-Gateway-Token") String gatewayToken, @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Roles") String roles) {
    authorizer.authorize(gatewayToken, operator, roles);
    return Map.of("items", service.findDead(limit));
  }

  @GetMapping("/{eventId}")
  public Object get(@PathVariable String eventId, @RequestHeader("X-Gateway-Token") String gatewayToken,
      @RequestHeader("X-User-Id") String operator, @RequestHeader("X-Roles") String roles) {
    authorizer.authorize(gatewayToken, operator, roles);
    return service.find(eventId);
  }

  @PostMapping("/{eventId}/redrive")
  public Object redrive(@PathVariable String eventId, @RequestBody RedriveRequest request,
      @RequestHeader("X-Gateway-Token") String gatewayToken, @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Roles") String roles,
      @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
    authorizer.authorize(gatewayToken, operator, roles);
    return service.redrive(eventId, operator, request.reason(), requestId);
  }

  public record RedriveRequest(String reason) {}
}
