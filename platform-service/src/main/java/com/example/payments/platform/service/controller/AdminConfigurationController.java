package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.admin.ConfigurationAdminService;
import lombok.RequiredArgsConstructor;
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
  public void createChannel(
      @RequestBody ConfigurationAdminService.ChannelRequest r, Authentication a) {
    service.createChannel(r, a);
  }

  @PutMapping("/channels/{id}/status")
  public void updateChannelStatus(
      @PathVariable("id") String id,
      @RequestBody ConfigurationAdminService.StatusRequest r,
      Authentication a) {
    service.updateChannelStatus(id, r, a);
  }

  @GetMapping("/routing-rules")
  public AdminPageResponse<ConfigurationAdminService.RoutingRuleResponse> routingRules(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.routingRules(p, s);
  }

  @PostMapping("/routing-rules")
  public void createRoutingRule(
      @RequestBody ConfigurationAdminService.RoutingRuleRequest r, Authentication a) {
    service.createRoutingRule(r, a);
  }

  @GetMapping("/pricing-rules")
  public AdminPageResponse<ConfigurationAdminService.PricingRuleResponse> pricingRules(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.pricingRules(p, s);
  }

  @PostMapping("/pricing-rules")
  public void createPricingRule(
      @RequestBody ConfigurationAdminService.PricingRuleRequest r, Authentication a) {
    service.createPricingRule(r, a);
  }

  @GetMapping("/risk-policies")
  public AdminPageResponse<ConfigurationAdminService.RiskPolicyResponse> riskPolicies(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.riskPolicies(p, s);
  }

  @PostMapping("/risk-policies")
  public void createRiskPolicy(
      @RequestBody ConfigurationAdminService.RiskPolicyRequest r, Authentication a) {
    service.createRiskPolicy(r, a);
  }
}
