package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.ConfigurationAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfigurationAdminService {
  private final ConfigurationAdminMapper mapper;
  private final OperationAuditService auditService;
  private final ObjectMapper objectMapper;
  private final com.example.payments.platform.service.mapper.PricingRuleMapper pricingRuleMapper;

  public Map<String, Object> overview() {
    return Map.of(
        "paymentSuccessRate",
        BigDecimal.ZERO,
        "paymentVolume",
        BigDecimal.ZERO,
        "activeMerchants",
        mapper.countActiveMerchants(),
        "activeChannels",
        mapper.countActiveChannels(),
        "pendingReleases",
        mapper.countPendingReleases(),
        "channelHealth",
        channels(1, 100).items().stream()
            .map(
                channel ->
                    Map.of(
                        "channelId",
                        channel.channelId(),
                        "name",
                        channel.name(),
                        "status",
                        channel.status().equals("ACTIVE") ? "UP" : "DOWN",
                        "successRate",
                        BigDecimal.ZERO))
            .toList());
  }

  public AdminPageResponse<ChannelResponse> channels(int page, int pageSize) {
    var q = pageQuery(page, pageSize);
    var total = mapper.countChannels();
    var items =
        mapper.selectChannels(q.size(), q.offset()).stream()
            .map(
                channel ->
                    new ChannelResponse(
                        channel.channelId(),
                        channel.name(),
                        channel.provider(),
                        channel.status(),
                        channel.weight(),
                        readMap(channel.configuration())))
            .toList();
    return new AdminPageResponse<>(items, q.page(), q.size(), total);
  }

  @Transactional
  public void createChannel(ChannelRequest request, Authentication authentication) {
    var now = Instant.now();
    mapper.insertChannel(
        request.channelId(), request.name(), request.provider(), request.weight(), json(request.configuration()), now);
    mapper.insertChannelCapability(
        java.util.UUID.randomUUID().toString(),
        request.channelId(),
        request.country(),
        request.currency(),
        request.paymentMethod(),
        request.minAmount(),
        request.maxAmount());
    audit(authentication.getName(), "CREATE", "CHANNEL", request.channelId(), request);
  }

  @Transactional
  public void updateChannelStatus(
      String channelId, StatusRequest request, Authentication authentication) {
    mapper.updateChannelStatus(channelId, request.status(), Instant.now());
    audit(authentication.getName(), "CHANGE_STATUS", "CHANNEL", channelId, request);
  }

  public AdminPageResponse<RoutingRuleResponse> routingRules(int page, int pageSize) {
    var q = pageQuery(page, pageSize);
    var total = mapper.countRoutingRules();
    var items = mapper.selectRoutingRules(q.size(), q.offset());
    return new AdminPageResponse<>(items, q.page(), q.size(), total);
  }

  @Transactional
  public void createRoutingRule(RoutingRuleRequest request, Authentication authentication) {
    var version = draftVersion(request.releaseId());
    mapper.insertRoutingRule(
        request.ruleId(), version, request.productCode(), request.merchantId(), request.paymentMethod(),
        request.country(), request.currency(), request.channelId(), request.priority(), request.weight());
    audit(authentication.getName(), "CREATE", "ROUTING_RULE", request.ruleId(), request);
  }

  @Transactional
  public void updateRoutingRuleStatus(
      String ruleId, StatusRequest request, Authentication authentication) {
    mapper.updateRoutingRuleStatus(ruleId, request.status(), Instant.now());
    audit(authentication.getName(), "CHANGE_STATUS", "ROUTING_RULE", ruleId, request);
  }

  public AdminPageResponse<PricingRuleResponse> pricingRules(int page, int pageSize) {
    var q = pageQuery(page, pageSize);
    var total = pricingRuleMapper.countAll();
    var items =
        pricingRuleMapper.selectByPage(q.offset(), q.size()).stream()
            .map(
                rule ->
                    new PricingRuleResponse(
                        rule.getRuleId(),
                        rule.getReleaseVersion(),
                        rule.getProductCode(),
                        rule.getMerchantId(),
                        rule.getCurrency(),
                        rule.getFeeRate(),
                        rule.getFixedFee(),
                        rule.getFeeMode(),
                        rule.getMinAmount(),
                        rule.getMaxAmount(),
                        rule.getStatus()))
            .toList();
    return new AdminPageResponse<>(items, q.page(), q.size(), total);
  }

  @Transactional
  public void createPricingRule(PricingRuleRequest request, Authentication authentication) {
    var version = draftVersion(request.releaseId());
    var rule = new com.example.payments.platform.service.model.PricingRuleFull();
    rule.setRuleId(request.ruleId());
    rule.setReleaseVersion(version);
    rule.setProductCode(request.productCode());
    rule.setMerchantId(request.merchantId());
    rule.setCurrency(request.currency());
    rule.setFeeRate(request.feeRate());
    rule.setFixedFee(request.fixedFee());
    rule.setFeeMode(request.feeMode());
    rule.setMinAmount(request.minAmount());
    rule.setMaxAmount(request.maxAmount());
    rule.setStatus("ACTIVE");
    pricingRuleMapper.insert(rule);
    audit(authentication.getName(), "CREATE", "PRICING_RULE", request.ruleId(), request);
  }

  @Transactional
  public void updatePricingRuleStatus(
      String ruleId, StatusRequest request, Authentication authentication) {
    pricingRuleMapper.updateStatus(ruleId, request.status());
    audit(authentication.getName(), "CHANGE_STATUS", "PRICING_RULE", ruleId, request);
  }

  public AdminPageResponse<RiskPolicyResponse> riskPolicies(int page, int pageSize) {
    var q = pageQuery(page, pageSize);
    var total = mapper.countRiskPolicies();
    var items =
        mapper.selectRiskPolicies(q.size(), q.offset()).stream()
            .map(
                policy ->
                    new RiskPolicyResponse(
                        policy.policyId(), policy.releaseVersion(), policy.name(), policy.priority(),
                        policy.decision(), readMap(policy.condition()), policy.status()))
            .toList();
    return new AdminPageResponse<>(items, q.page(), q.size(), total);
  }

  @Transactional
  public void createRiskPolicy(RiskPolicyRequest request, Authentication authentication) {
    var version = draftVersion(request.releaseId());
    mapper.insertRiskPolicy(
        request.policyId(), version, request.name(), request.priority(), request.decision(), json(request.condition()));
    audit(authentication.getName(), "CREATE", "RISK_POLICY", request.policyId(), request);
  }

  @Transactional
  public void updateRiskPolicyStatus(
      String policyId, StatusRequest request, Authentication authentication) {
    mapper.updateRiskPolicyStatus(policyId, request.status(), Instant.now());
    audit(authentication.getName(), "CHANGE_STATUS", "RISK_POLICY", policyId, request);
  }

  private long draftVersion(String releaseId) {
    return mapper.selectDraftVersion(releaseId);
  }

  private PageQuery pageQuery(int page, int pageSize) {
    return new PageQuery(page, pageSize);
  }

  private record PageQuery(int page, int size) {
    PageQuery {
      page = Math.max(page, 1);
      size = Math.min(Math.max(size, 1), 100);
    }

    int offset() {
      return (page - 1) * size;
    }
  }

  private void audit(String operator, String action, String type, String id, Object after) {
    auditService.record(operator, action, type, id, after);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("内容不是合法 JSON", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readMap(String value) {
    try {
      return objectMapper.readValue(value, Map.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("数据库 JSON 无法解析", exception);
    }
  }

  public record MerchantRequest(
      @NotBlank String merchantId,
      @NotBlank String name,
      @Pattern(regexp = "[A-Z]{3}") String settlementCurrency) {}

  public record MerchantResponse(
      String merchantId,
      String name,
      String status,
      String settlementCurrency,
      Instant createdAt,
      Instant updatedAt) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record ProductRequest(
      @NotBlank String productCode,
      @NotBlank String name,
      @NotBlank String country,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethod,
      @DecimalMin("0.01") BigDecimal minAmount,
      @Positive BigDecimal maxAmount,
      boolean supportsRefund) {}

  public record ProductResponse(
      String productCode,
      String name,
      String status,
      String paymentMethod,
      String country,
      String currency,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      Boolean supportsRefund) {}

  public record ChannelRequest(
      @NotBlank String channelId,
      @NotBlank String name,
      @NotBlank String provider,
      @NotNull Integer weight,
      @NotNull Map<String, Object> configuration,
      @NotBlank String country,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethod,
      @DecimalMin("0.01") BigDecimal minAmount,
      @Positive BigDecimal maxAmount) {}

  public record ChannelResponse(
      String channelId,
      String name,
      String provider,
      String status,
      int weight,
      Map<String, Object> configuration) {}

  public record ChannelRow(
      String channelId, String name, String provider, String status, int weight, String configuration) {}

  public record RoutingRuleRequest(
      @NotBlank String ruleId,
      @NotBlank String releaseId,
      @NotBlank String productCode,
      String merchantId,
      @NotBlank String paymentMethod,
      String country,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String channelId,
      @Positive int priority,
      @Positive int weight) {}

  public record RoutingRuleResponse(
      String ruleId,
      long releaseVersion,
      String productCode,
      String merchantId,
      String paymentMethod,
      String country,
      String currency,
      String channelId,
      int priority,
      int weight,
      String status) {}

  public record PricingRuleRequest(
      @NotBlank String ruleId,
      @NotBlank String releaseId,
      @NotBlank String productCode,
      String merchantId,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @DecimalMin("0.000000") BigDecimal feeRate,
      @DecimalMin("0.00") BigDecimal fixedFee,
      @Pattern(regexp = "INCLUSIVE|EXCLUSIVE") String feeMode,
      @DecimalMin("0.01") BigDecimal minAmount,
      @Positive BigDecimal maxAmount) {}

  public record PricingRuleResponse(
      String ruleId,
      long releaseVersion,
      String productCode,
      String merchantId,
      String currency,
      BigDecimal feeRate,
      BigDecimal fixedFee,
      String feeMode,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String status) {}

  public record RiskPolicyRequest(
      @NotBlank String policyId,
      @NotBlank String releaseId,
      @NotBlank String name,
      @Positive int priority,
      @Pattern(regexp = "PASS|REJECT|REVIEW") String decision,
      @NotNull Map<String, Object> condition) {}

  public record RiskPolicyResponse(
      String policyId,
      long releaseVersion,
      String name,
      int priority,
      String decision,
      Map<String, Object> condition,
      String status) {}

  public record RiskPolicyRow(
      String policyId,
      long releaseVersion,
      String name,
      int priority,
      String decision,
      String condition,
      String status) {}
}
