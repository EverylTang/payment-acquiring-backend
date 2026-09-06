package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.ConfigurationAdminService;
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
  @PreAuthorize("hasAuthority('dashboard:overview')")
  public java.util.Map<String, Object> overview() {
    return service.overview();
  }

  @GetMapping("/channels")
  @PreAuthorize("hasAuthority('channel:list')")
  public AdminPageResponse<ConfigurationAdminService.ChannelResponse> channels(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    return service.channels(page, pageSize);
  }

  @PostMapping("/channels")
  @PreAuthorize("hasAuthority('channel:create')")
  public void createChannel(
      @Valid @RequestBody ConfigurationAdminService.ChannelRequest r, Authentication a) {
    service.createChannel(r, a);
  }

  @PutMapping("/channels/{id}")
  @PreAuthorize("hasAuthority('channel:update')")
  public void updateChannel(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.ChannelUpdateRequest r,
      Authentication a) {
    service.updateChannel(id, r, a);
  }

  @PutMapping("/channels/{id}/status")
  @PreAuthorize("hasAuthority('channel:status')")
  public void updateChannelStatus(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.StatusRequest r,
      Authentication a) {
    service.updateChannelStatus(id, r, a);
  }

  @GetMapping("/routing-rules")
  @PreAuthorize("hasAuthority('routing-rule:list')")
  public AdminPageResponse<ConfigurationAdminService.RoutingRuleResponse> routingRules(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.routingRules(p, s);
  }

  @PostMapping("/routing-rules")
  @PreAuthorize("hasAuthority('routing-rule:create')")
  public void createRoutingRule(
      @Valid @RequestBody ConfigurationAdminService.RoutingRuleRequest r, Authentication a) {
    service.createRoutingRule(r, a);
  }

  @PutMapping("/routing-rules/{id}")
  @PreAuthorize("hasAuthority('routing-rule:update')")
  public void updateRoutingRule(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.RoutingRuleUpdateRequest r,
      Authentication a) {
    service.updateRoutingRule(id, r, a);
  }

  @PutMapping("/routing-rules/{id}/status")
  @PreAuthorize("hasAuthority('routing-rule:status')")
  public void updateRoutingRuleStatus(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.StatusRequest r,
      Authentication a) {
    service.updateRoutingRuleStatus(id, r, a);
  }

  @GetMapping("/pricing-rules")
  @PreAuthorize("hasAuthority('pricing-rule:list')")
  public AdminPageResponse<ConfigurationAdminService.PricingRuleResponse> pricingRules(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.pricingRules(p, s);
  }

  @PostMapping("/pricing-rules")
  @PreAuthorize("hasAuthority('pricing-rule:create')")
  public void createPricingRule(
      @Valid @RequestBody ConfigurationAdminService.PricingRuleRequest r, Authentication a) {
    service.createPricingRule(r, a);
  }

  @PutMapping("/pricing-rules/{id}")
  @PreAuthorize("hasAuthority('pricing-rule:update')")
  public void updatePricingRule(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.PricingRuleUpdateRequest r,
      Authentication a) {
    service.updatePricingRule(id, r, a);
  }

  @PutMapping("/pricing-rules/{id}/status")
  @PreAuthorize("hasAuthority('pricing-rule:status')")
  public void updatePricingRuleStatus(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.StatusRequest r,
      Authentication a) {
    service.updatePricingRuleStatus(id, r, a);
  }

  @GetMapping("/risk-policies")
  @PreAuthorize("hasAuthority('risk-policy:list')")
  public AdminPageResponse<ConfigurationAdminService.RiskPolicyResponse> riskPolicies(
      @RequestParam(defaultValue = "1") int p, @RequestParam(defaultValue = "20") int s) {
    return service.riskPolicies(p, s);
  }

  @PostMapping("/risk-policies")
  @PreAuthorize("hasAuthority('risk-policy:create')")
  public void createRiskPolicy(
      @Valid @RequestBody ConfigurationAdminService.RiskPolicyRequest r, Authentication a) {
    service.createRiskPolicy(r, a);
  }

  @PutMapping("/risk-policies/{id}")
  @PreAuthorize("hasAuthority('risk-policy:update')")
  public void updateRiskPolicy(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.RiskPolicyUpdateRequest r,
      Authentication a) {
    service.updateRiskPolicy(id, r, a);
  }

  @PutMapping("/risk-policies/{id}/status")
  @PreAuthorize("hasAuthority('risk-policy:status')")
  public void updateRiskPolicyStatus(
      @PathVariable("id") String id,
      @Valid @RequestBody ConfigurationAdminService.StatusRequest r,
      Authentication a) {
    service.updateRiskPolicyStatus(id, r, a);
  }
}
