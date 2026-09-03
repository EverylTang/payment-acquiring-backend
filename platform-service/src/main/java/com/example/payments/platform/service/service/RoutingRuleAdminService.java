package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.RoutingRuleMapper;
import com.example.payments.platform.service.model.RoutingRuleFull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoutingRuleAdminService {
  private final RoutingRuleMapper mapper;
  private final PlatformDataService mybatisClient;

  public AdminPageResponse<RoutingRuleResponse> list(int page, int pageSize) {
    page = Math.max(page, 1);
    pageSize = Math.clamp(pageSize, 1, 100);
    int offset = (page - 1) * pageSize;
    List<RoutingRuleFull> rules = mapper.selectByPage(offset, pageSize);
    int total = mapper.countAll();

    List<RoutingRuleResponse> items =
        rules.stream()
            .map(
                r ->
                    new RoutingRuleResponse(
                        r.getRuleId(),
                        r.getReleaseVersion(),
                        r.getProductCode(),
                        r.getMerchantId(),
                        r.getCountry(),
                        r.getCurrency(),
                        r.getPaymentMethod(),
                        r.getChannelId(),
                        r.getPriority(),
                        r.getWeight(),
                        r.getStatus()))
            .toList();

    return new AdminPageResponse<>(items, total, page, pageSize);
  }

  public RoutingRuleResponse detail(String ruleId) {
    RoutingRuleFull rule = mapper.selectByRuleId(ruleId);
    if (rule == null) {
      throw new IllegalArgumentException("Routing rule not found: " + ruleId);
    }
    return new RoutingRuleResponse(
        rule.getRuleId(),
        rule.getReleaseVersion(),
        rule.getProductCode(),
        rule.getMerchantId(),
        rule.getCountry(),
        rule.getCurrency(),
        rule.getPaymentMethod(),
        rule.getChannelId(),
        rule.getPriority(),
        rule.getWeight(),
        rule.getStatus());
  }

  @Transactional
  public RoutingRuleResponse create(RoutingRuleRequest request, Authentication auth) {
    String ruleId = "RR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    RoutingRuleFull rule = new RoutingRuleFull();
    rule.setRuleId(ruleId);
    rule.setReleaseVersion(request.releaseVersion());
    rule.setProductCode(request.productCode());
    rule.setMerchantId(request.merchantId());
    rule.setCountry(request.country());
    rule.setCurrency(request.currency());
    rule.setPaymentMethod(request.paymentMethod());
    rule.setChannelId(request.channelId());
    rule.setPriority(request.priority());
    rule.setWeight(request.weight());
    rule.setStatus("ACTIVE");

    mapper.insert(rule);
    audit(auth.getName(), "CREATE_ROUTING_RULE", ruleId);

    return detail(ruleId);
  }

  @Transactional
  public RoutingRuleResponse update(
      String ruleId, RoutingRuleRequest request, Authentication auth) {
    RoutingRuleFull existing = mapper.selectByRuleId(ruleId);
    if (existing == null) {
      throw new IllegalArgumentException("Routing rule not found: " + ruleId);
    }

    existing.setReleaseVersion(request.releaseVersion());
    existing.setProductCode(request.productCode());
    existing.setMerchantId(request.merchantId());
    existing.setCountry(request.country());
    existing.setCurrency(request.currency());
    existing.setPaymentMethod(request.paymentMethod());
    existing.setChannelId(request.channelId());
    existing.setPriority(request.priority());
    existing.setWeight(request.weight());

    mapper.update(existing);
    audit(auth.getName(), "UPDATE_ROUTING_RULE", ruleId);

    return detail(ruleId);
  }

  @Transactional
  public void updateStatus(String ruleId, String status, Authentication auth) {
    RoutingRuleFull existing = mapper.selectByRuleId(ruleId);
    if (existing == null) {
      throw new IllegalArgumentException("Routing rule not found: " + ruleId);
    }

    mapper.updateStatus(ruleId, status);
    audit(auth.getName(), "UPDATE_ROUTING_RULE_STATUS", ruleId);
  }

  @Transactional
  public void delete(String ruleId, Authentication auth) {
    RoutingRuleFull existing = mapper.selectByRuleId(ruleId);
    if (existing == null) {
      throw new IllegalArgumentException("Routing rule not found: " + ruleId);
    }

    mapper.deleteByRuleId(ruleId);
    audit(auth.getName(), "DELETE_ROUTING_RULE", ruleId);
  }

  private void audit(String username, String action, String targetId) {
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, created_at) VALUES (:audit, :operator, :action,"
                + " 'ROUTING_RULE', :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", username)
        .param("action", action)
        .param("resourceId", targetId)
        .param("now", Instant.now())
        .update();
  }

  public record RoutingRuleRequest(
      @NotNull Long releaseVersion,
      @NotBlank String productCode,
      String merchantId,
      @NotBlank String country,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethod,
      @NotBlank String channelId,
      @NotNull @Positive Integer priority,
      @NotNull @Positive Integer weight) {}

  public record RoutingRuleResponse(
      String ruleId,
      Long releaseVersion,
      String productCode,
      String merchantId,
      String country,
      String currency,
      String paymentMethod,
      String channelId,
      Integer priority,
      Integer weight,
      String status) {}

  public record StatusRequest(@NotBlank String status) {}
}
