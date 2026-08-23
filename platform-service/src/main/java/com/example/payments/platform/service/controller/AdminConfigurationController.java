package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.admin.ConfigurationAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1")
@RequiredArgsConstructor
public class AdminConfigurationController {
  private final ConfigurationAdminService service;

  @GetMapping("/dashboard/overview")
  public java.util.Map<String, Object> overview() {
    return service.overview();
  }

  @GetMapping("/channels")
  public AdminPageResponse<ConfigurationAdminService.ChannelResponse> channels(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    return service.channels(page, pageSize);
  }

  @PostMapping("/channels")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public void createChannel(
      @Valid @RequestBody ConfigurationAdminService.ChannelRequest r, Authentication a) {
    service.createChannel(r, a);
  }

  @PutMapping("/channels/{id}/status")
  @PreAuthorize("hasRole('ADMIN')")
  public void updateChannelStatus(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.StatusRequest r,
      Authentication a) {
    service.updateChannelStatus(id, r, a);
  }

  @GetMapping("/routing-rules")
  public AdminPageResponse<ConfigurationAdminService.RoutingRuleResponse> routingRules(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.routingRules(p, s);
  }

  @PostMapping("/routing-rules")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  public void createRoutingRule(
      @Valid @RequestBody ConfigurationAdminService.RoutingRuleRequest r, Authentication a) {
    service.createRoutingRule(r, a);
  }

  @GetMapping("/pricing-rules")
  public AdminPageResponse<ConfigurationAdminService.PricingRuleResponse> pricingRules(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.pricingRules(p, s);
  }

  @PostMapping("/pricing-rules")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'FINANCE')")
  public void createPricingRule(
      @Valid @RequestBody ConfigurationAdminService.PricingRuleRequest r, Authentication a) {
    service.createPricingRule(r, a);
  }

  @GetMapping("/risk-policies")
  public AdminPageResponse<ConfigurationAdminService.RiskPolicyResponse> riskPolicies(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.riskPolicies(p, s);
  }

  @PostMapping("/risk-policies")
  @PreAuthorize("hasAnyRole('ADMIN', 'RISK')")
  public void createRiskPolicy(
      @Valid @RequestBody ConfigurationAdminService.RiskPolicyRequest r, Authentication a) {
    service.createRiskPolicy(r, a);
  }
}
