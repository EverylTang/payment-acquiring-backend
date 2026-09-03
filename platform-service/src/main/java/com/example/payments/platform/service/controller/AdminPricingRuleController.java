package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.PricingRuleAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/pricing-rules-mgmt")
@RequiredArgsConstructor
public class AdminPricingRuleController {
  private final PricingRuleAdminService service;

  @GetMapping
  public AdminPageResponse<PricingRuleAdminService.PricingRuleResponse> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    return service.list(page, pageSize);
  }

  @GetMapping("/{ruleId}")
  public PricingRuleAdminService.PricingRuleResponse detail(@PathVariable String ruleId) {
    return service.detail(ruleId);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
  public PricingRuleAdminService.PricingRuleResponse create(
      @Valid @RequestBody PricingRuleAdminService.PricingRuleRequest request, Authentication auth) {
    return service.create(request, auth);
  }

  @PutMapping("/{ruleId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
  public PricingRuleAdminService.PricingRuleResponse update(
      @PathVariable String ruleId,
      @Valid @RequestBody PricingRuleAdminService.PricingRuleRequest request,
      Authentication auth) {
    return service.update(ruleId, request, auth);
  }

  @PutMapping("/{ruleId}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public void updateStatus(
      @PathVariable String ruleId,
      @Valid @RequestBody PricingRuleAdminService.StatusRequest request,
      Authentication auth) {
    service.updateStatus(ruleId, request.status(), auth);
  }

  @DeleteMapping("/{ruleId}")
  @PreAuthorize("hasRole('ADMIN')")
  public void delete(@PathVariable String ruleId, Authentication auth) {
    service.delete(ruleId, auth);
  }
}
