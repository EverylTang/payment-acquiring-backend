package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.ConfigurationAdminMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfigurationAdminService {
  private static final Set<String> SIGNATURE_PROFILES =
      Set.of(
          "NONE",
          "DEFAULT",
          "SIMULATED_SHA256_PREFIX_V1",
          "MD5_KEY_SUFFIX_V1",
          "SHA256_KEY_SUFFIX_V1",
          "HMAC_SHA256_V1",
          "HMAC_SHA512_V1",
          "RSA_SHA256_V1");
  private final ConfigurationAdminMapper mapper;
  private final OperationAuditService auditService;
  private final ObjectMapper objectMapper;
  private final com.example.payments.platform.service.mapper.PricingRuleMapper pricingRuleMapper;
  private final Environment environment;

  @PostConstruct
  void rejectActiveSimulatedChannels() {
    if (!simulationProfile() && mapper.countActiveChannelsByProvider("SIMULATED") > 0) {
      throw new IllegalStateException(
          "SIMULATED channels are only permitted in local or test profiles");
    }
  }

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
                        channel.requestUrl(),
                        channel.signatureProfile(),
                        channel.status(),
                        channelSettings(channel.configuration()),
                        channelCredentials(channel.configuration())))
            .toList();
    return new AdminPageResponse<>(items, q.page(), q.size(), total);
  }

  @Transactional
  public void createChannel(ChannelRequest request, Authentication authentication) {
    validateSignatureProfile(request.signatureProfile());
    validateProvider(request.provider(), request.signatureProfile());
    var now = Instant.now();
    mapper.insertChannel(
        request.channelId(),
        request.name(),
        request.provider(),
        request.requestUrl(),
        request.signatureProfile(),
        json(channelDocument(request.configuration(), request.credentials())),
        now);
    mapper.insertChannelCapability(
        java.util.UUID.randomUUID().toString(),
        request.channelId(),
        request.country(),
        request.currency(),
        request.paymentMethod(),
        request.minAmount(),
        request.maxAmount());
    audit(
        authentication.getName(),
        "CREATE",
        "CHANNEL",
        request.channelId(),
        channelAudit(
            request.name(),
            request.provider(),
            request.requestUrl(),
            request.signatureProfile(),
            request.configuration(),
            request.credentials()));
  }

  @Transactional
  public void updateChannel(
      String channelId, ChannelUpdateRequest request, Authentication authentication) {
    validateSignatureProfile(request.signatureProfile());
    validateProvider(request.provider(), request.signatureProfile());
    if (mapper.updateChannel(
            channelId,
            request.name(),
            request.provider(),
            request.requestUrl(),
            request.signatureProfile(),
            json(channelDocument(request.configuration(), request.credentials())),
            Instant.now())
        != 1) {
      throw new IllegalArgumentException("渠道不存在: " + channelId);
    }
    audit(
        authentication.getName(),
        "UPDATE",
        "CHANNEL",
        channelId,
        channelAudit(
            request.name(),
            request.provider(),
            request.requestUrl(),
            request.signatureProfile(),
            request.configuration(),
            request.credentials()));
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
        request.ruleId(),
        version,
        request.productCode(),
        request.merchantId(),
        request.paymentMethod(),
        request.country(),
        request.currency(),
        request.channelId(),
        request.priority(),
        request.weight());
    audit(authentication.getName(), "CREATE", "ROUTING_RULE", request.ruleId(), request);
  }

  @Transactional
  public void updateRoutingRule(
      String ruleId, RoutingRuleUpdateRequest request, Authentication authentication) {
    if (mapper.updateRoutingRule(
            ruleId,
            request.productCode(),
            request.merchantId(),
            request.paymentMethod(),
            request.country(),
            request.currency(),
            request.channelId(),
            request.priority(),
            request.weight())
        != 1) {
      throw new IllegalArgumentException("路由规则不存在: " + ruleId);
    }
    audit(authentication.getName(), "UPDATE", "ROUTING_RULE", ruleId, request);
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
                        rule.getChannelId(),
                        rule.getCurrency(),
                        rule.getFeeRate(),
                        rule.getFixedFee(),
                        rule.getExtraFee(),
                        rule.getMinFee(),
                        rule.getMaxFee(),
                        rule.getFeeType(),
                        readTiers(rule.getTieredFees()),
                        rule.getFeeMode(),
                        rule.getMinAmount(),
                        rule.getMaxAmount(),
                        rule.getStatus()))
            .toList();
    return new AdminPageResponse<>(items, q.page(), q.size(), total);
  }

  @Transactional
  public void createPricingRule(PricingRuleRequest request, Authentication authentication) {
    PricingFeeRules.validate(
        request.feeType(),
        request.feeRate(),
        request.fixedFee(),
        request.extraFee(),
        request.minFee(),
        request.maxFee(),
        request.tiers());
    var version = draftVersion(request.releaseId());
    var rule = new com.example.payments.platform.service.model.PricingRuleFull();
    rule.setRuleId(request.ruleId());
    rule.setReleaseVersion(version);
    rule.setProductCode(request.productCode());
    rule.setMerchantId(request.merchantId());
    rule.setChannelId(request.channelId());
    rule.setCurrency(request.currency());
    rule.setFeeRate(request.feeRate());
    rule.setFixedFee(request.fixedFee());
    rule.setExtraFee(request.extraFee());
    rule.setMinFee(request.minFee());
    rule.setMaxFee(request.maxFee());
    rule.setFeeType(request.feeType());
    rule.setTieredFees(
        request.feeType().equals(PricingFeeRules.TIERED) ? json(request.tiers()) : null);
    rule.setFeeMode(request.feeMode());
    rule.setMinAmount(request.minAmount());
    rule.setMaxAmount(request.maxAmount());
    rule.setStatus("ACTIVE");
    pricingRuleMapper.insert(rule);
    audit(authentication.getName(), "CREATE", "PRICING_RULE", request.ruleId(), request);
  }

  @Transactional
  public void updatePricingRule(
      String ruleId, PricingRuleUpdateRequest request, Authentication authentication) {
    var existing = pricingRuleMapper.selectByRuleId(ruleId);
    if (existing == null) throw new IllegalArgumentException("费率规则不存在: " + ruleId);
    if (!pricingRuleMapper.isDraftVersion(existing.getReleaseVersion())) {
      throw new IllegalStateException("已发布或审核中的费率规则不可编辑，请创建草稿版本后调整");
    }
    PricingFeeRules.validate(
        request.feeType(),
        request.feeRate(),
        request.fixedFee(),
        request.extraFee(),
        request.minFee(),
        request.maxFee(),
        request.tiers());
    existing.setProductCode(request.productCode());
    existing.setMerchantId(request.merchantId());
    existing.setChannelId(request.channelId());
    existing.setCurrency(request.currency());
    existing.setFeeRate(request.feeRate());
    existing.setFixedFee(request.fixedFee());
    existing.setExtraFee(request.extraFee());
    existing.setMinFee(request.minFee());
    existing.setMaxFee(request.maxFee());
    existing.setFeeType(request.feeType());
    existing.setTieredFees(
        request.feeType().equals(PricingFeeRules.TIERED) ? json(request.tiers()) : null);
    existing.setFeeMode(request.feeMode());
    existing.setMinAmount(request.minAmount());
    existing.setMaxAmount(request.maxAmount());
    pricingRuleMapper.update(existing);
    audit(authentication.getName(), "UPDATE", "PRICING_RULE", ruleId, request);
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
                        policy.policyId(),
                        policy.releaseVersion(),
                        policy.name(),
                        policy.priority(),
                        policy.decision(),
                        readMap(policy.condition()),
                        policy.status()))
            .toList();
    return new AdminPageResponse<>(items, q.page(), q.size(), total);
  }

  @Transactional
  public void createRiskPolicy(RiskPolicyRequest request, Authentication authentication) {
    var version = draftVersion(request.releaseId());
    mapper.insertRiskPolicy(
        request.policyId(),
        version,
        request.name(),
        request.priority(),
        request.decision(),
        json(request.condition()));
    audit(authentication.getName(), "CREATE", "RISK_POLICY", request.policyId(), request);
  }

  @Transactional
  public void updateRiskPolicyStatus(
      String policyId, StatusRequest request, Authentication authentication) {
    mapper.updateRiskPolicyStatus(policyId, request.status(), Instant.now());
    audit(authentication.getName(), "CHANGE_STATUS", "RISK_POLICY", policyId, request);
  }

  @Transactional
  public void updateRiskPolicy(
      String policyId, RiskPolicyUpdateRequest request, Authentication authentication) {
    if (mapper.updateRiskPolicy(
            policyId,
            request.name(),
            request.priority(),
            request.decision(),
            json(request.condition()))
        == 0) {
      throw new IllegalArgumentException("策略不存在，或所属版本不是可编辑草稿: " + policyId);
    }
    audit(authentication.getName(), "UPDATE", "RISK_POLICY", policyId, request);
  }

  private long draftVersion(String releaseId) {
    return java.util.Optional.ofNullable(mapper.selectDraftVersion(releaseId))
        .orElseThrow(() -> new IllegalArgumentException("草稿版本不存在或当前不可编辑: " + releaseId));
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

  private Map<String, Object> channelAudit(
      String name,
      String provider,
      String requestUrl,
      String signatureProfile,
      Map<String, Object> configuration,
      Map<String, Object> credentials) {
    return Map.of(
        "name",
        name,
        "provider",
        provider,
        "requestUrl",
        requestUrl,
        "signatureProfile",
        signatureProfile,
        "configurationKeys",
        configuration.keySet(),
        "credentialKeys", credentials.keySet());
  }

  private Map<String, Object> channelDocument(
      Map<String, Object> configuration, Map<String, Object> credentials) {
    return Map.of("settings", configuration, "credentials", credentials);
  }

  private Map<String, Object> channelSettings(String configuration) {
    var document = readMap(configuration);
    var settings = document.get("settings");
    return settings instanceof Map<?, ?> ? castMap(settings) : document;
  }

  private Map<String, Object> channelCredentials(String configuration) {
    var value = readMap(configuration).get("credentials");
    return value instanceof Map<?, ?> ? castMap(value) : Map.of();
  }

  private void validateSignatureProfile(String signatureProfile) {
    if (!SIGNATURE_PROFILES.contains(signatureProfile)) {
      throw new IllegalArgumentException("不支持的渠道签名方案: " + signatureProfile);
    }
  }

  private void validateProvider(String provider, String signatureProfile) {
    boolean simulated = "SIMULATED".equalsIgnoreCase(provider);
    boolean simulatedSignature = signatureProfile.toUpperCase(Locale.ROOT).startsWith("SIMULATED_");
    if ((simulated || simulatedSignature) && !simulationProfile()) {
      throw new IllegalArgumentException(
          "SIMULATED channels are only permitted in local or test profiles");
    }
  }

  private boolean simulationProfile() {
    return java.util.Arrays.stream(environment.getActiveProfiles())
        .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));
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

  private Map<String, Object> castMap(Object value) {
    var result = new java.util.LinkedHashMap<String, Object>();
    ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
    return result;
  }

  private List<PricingFeeRules.FeeTier> readTiers(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return objectMapper.readValue(
          value,
          objectMapper
              .getTypeFactory()
              .constructCollectionType(List.class, PricingFeeRules.FeeTier.class));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("数据库阶梯手续费配置无法解析", exception);
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
      @NotBlank @Pattern(regexp = "https?://[^\\s]+") String requestUrl,
      @NotBlank String signatureProfile,
      @NotNull Map<String, Object> configuration,
      @NotNull Map<String, Object> credentials,
      @NotBlank String country,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethod,
      @DecimalMin("0.01") BigDecimal minAmount,
      @Positive BigDecimal maxAmount) {}

  public record ChannelUpdateRequest(
      @NotBlank String name,
      @NotBlank String provider,
      @NotBlank @Pattern(regexp = "https?://[^\\s]+") String requestUrl,
      @NotBlank String signatureProfile,
      @NotNull Map<String, Object> configuration,
      @NotNull Map<String, Object> credentials) {}

  public record ChannelResponse(
      String channelId,
      String name,
      String provider,
      String requestUrl,
      String signatureProfile,
      String status,
      Map<String, Object> configuration,
      Map<String, Object> credentials) {}

  public record ChannelRow(
      String channelId,
      String name,
      String provider,
      String requestUrl,
      String signatureProfile,
      String status,
      String configuration) {}


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

  public record RoutingRuleUpdateRequest(
      @NotBlank String productCode,
      String merchantId,
      @NotBlank String paymentMethod,
      String country,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String channelId,
      @Positive int priority,
      @Positive int weight) {}

  public record PricingRuleRequest(
      @NotBlank String ruleId,
      @NotBlank String releaseId,
      @NotBlank String productCode,
      String merchantId,
      String channelId,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotNull @DecimalMin("0.000000") BigDecimal feeRate,
      @NotNull @DecimalMin("0.00") BigDecimal fixedFee,
      @NotNull @DecimalMin("0.00") BigDecimal extraFee,
      @DecimalMin("0.00") BigDecimal minFee,
      @DecimalMin("0.00") BigDecimal maxFee,
      @NotBlank @Pattern(regexp = "FIXED|PERCENTAGE|TIERED|COMBINED") String feeType,
      List<PricingFeeRules.FeeTier> tiers,
      @NotBlank @Pattern(regexp = "PAYER_BEAR|MERCHANT_BEAR|INCLUSIVE|EXCLUSIVE") String feeMode,
      @NotNull @DecimalMin("0.01") BigDecimal minAmount,
      @NotNull @Positive BigDecimal maxAmount) {}

  public record PricingRuleResponse(
      String ruleId,
      long releaseVersion,
      String productCode,
      String merchantId,
      String channelId,
      String currency,
      BigDecimal feeRate,
      BigDecimal fixedFee,
      BigDecimal extraFee,
      BigDecimal minFee,
      BigDecimal maxFee,
      String feeType,
      List<PricingFeeRules.FeeTier> tiers,
      String feeMode,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String status) {}

  public record PricingRuleUpdateRequest(
      @NotBlank String productCode,
      String merchantId,
      String channelId,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotNull @DecimalMin("0.000000") BigDecimal feeRate,
      @NotNull @DecimalMin("0.00") BigDecimal fixedFee,
      @NotNull @DecimalMin("0.00") BigDecimal extraFee,
      @DecimalMin("0.00") BigDecimal minFee,
      @DecimalMin("0.00") BigDecimal maxFee,
      @NotBlank @Pattern(regexp = "FIXED|PERCENTAGE|TIERED|COMBINED") String feeType,
      List<PricingFeeRules.FeeTier> tiers,
      @NotBlank @Pattern(regexp = "PAYER_BEAR|MERCHANT_BEAR|INCLUSIVE|EXCLUSIVE") String feeMode,
      @NotNull @DecimalMin("0.01") BigDecimal minAmount,
      @NotNull @Positive BigDecimal maxAmount) {}

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

  public record RiskPolicyUpdateRequest(
      @NotBlank String name,
      @Positive int priority,
      @Pattern(regexp = "PASS|REJECT|REVIEW") String decision,
      @NotNull Map<String, Object> condition) {}

  public record RiskPolicyRow(
      String policyId,
      long releaseVersion,
      String name,
      int priority,
      String decision,
      String condition,
      String status) {}
}
