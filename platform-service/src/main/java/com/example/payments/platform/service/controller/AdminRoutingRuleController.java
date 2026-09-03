package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.RoutingRuleAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/routing-rules-mgmt")
@RequiredArgsConstructor
public class AdminRoutingRuleController {
  private final RoutingRuleAdminService service;

  @GetMapping
  @PreAuthorize("hasAuthority('routing-rule:list')")
  public AdminPageResponse<RoutingRuleAdminService.RoutingRuleResponse> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    return service.list(page, pageSize);
  }

  @GetMapping("/{ruleId}")
  @PreAuthorize("hasAuthority('routing-rule:detail')")
  public RoutingRuleAdminService.RoutingRuleResponse detail(@PathVariable String ruleId) {
    return service.detail(ruleId);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('routing-rule:create')")
  public RoutingRuleAdminService.RoutingRuleResponse create(
      @Valid @RequestBody RoutingRuleAdminService.RoutingRuleRequest request, Authentication auth) {
    return service.create(request, auth);
  }

  @PutMapping("/{ruleId}")
  @PreAuthorize("hasAuthority('routing-rule:update')")
  public RoutingRuleAdminService.RoutingRuleResponse update(
      @PathVariable String ruleId,
      @Valid @RequestBody RoutingRuleAdminService.RoutingRuleRequest request,
      Authentication auth) {
    return service.update(ruleId, request, auth);
  }

  @PutMapping("/{ruleId}/status")
  @PreAuthorize("hasAuthority('routing-rule:status')")
  public void updateStatus(
      @PathVariable String ruleId,
      @Valid @RequestBody RoutingRuleAdminService.StatusRequest request,
      Authentication auth) {
    service.updateStatus(ruleId, request.status(), auth);
  }

  @DeleteMapping("/{ruleId}")
  @PreAuthorize("hasAuthority('routing-rule:delete')")
  public void delete(@PathVariable String ruleId, Authentication auth) {
    service.delete(ruleId, auth);
  }
}
