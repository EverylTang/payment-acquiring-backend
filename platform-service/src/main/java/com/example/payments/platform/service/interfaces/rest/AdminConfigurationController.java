package com.example.payments.platform.service.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1")
public class AdminConfigurationController {
  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public AdminConfigurationController(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/dashboard/overview")
  public Map<String, Object> overview() {
    return Map.of(
        "paymentSuccessRate", BigDecimal.ZERO,
        "paymentVolume", BigDecimal.ZERO,
        "activeMerchants", count("merchant", "status = 'ACTIVE'"),
        "activeChannels", count("channel", "status = 'ACTIVE'"),
        "pendingReleases", count("config_release", "status IN ('DRAFT', 'IN_REVIEW', 'APPROVED')"),
        "channelHealth", channels().stream().map(channel -> Map.of(
            "channelId", channel.channelId(), "name", channel.name(),
            "status", channel.status().equals("ACTIVE") ? "UP" : "DOWN", "successRate", BigDecimal.ZERO)).toList());
  }

  @GetMapping("/merchants")
  public List<MerchantResponse> merchants() {
    return jdbcClient.sql("SELECT merchant_id, name, status, settlement_currency, created_at, updated_at FROM merchant ORDER BY created_at DESC")
        .query(MerchantResponse.class).list();
  }

  @PostMapping("/merchants")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public MerchantResponse createMerchant(@Valid @RequestBody MerchantRequest request, Authentication authentication) {
    var now = Timestamp.from(Instant.now());
    jdbcClient.sql("INSERT INTO merchant (merchant_id, name, status, settlement_currency, created_at, updated_at) VALUES (:id, :name, 'ACTIVE', :currency, :now, :now)")
        .param("id", request.merchantId()).param("name", request.name()).param("currency", request.settlementCurrency()).param("now", now).update();
    audit(authentication.getName(), "CREATE", "MERCHANT", request.merchantId(), request);
    return merchants().stream().filter(item -> item.merchantId().equals(request.merchantId())).findFirst().orElseThrow();
  }

  @PutMapping("/merchants/{merchantId}/status")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public void updateMerchantStatus(@PathVariable String merchantId, @Valid @RequestBody StatusRequest request, Authentication authentication) {
    updateStatus("merchant", "merchant_id", merchantId, request.status());
    audit(authentication.getName(), "CHANGE_STATUS", "MERCHANT", merchantId, request);
  }

  @GetMapping("/products")
  public List<ProductResponse> products() {
    return jdbcClient.sql("SELECT p.product_code, p.name, p.status, c.payment_method, c.country, c.currency, c.min_amount, c.max_amount, c.supports_refund FROM logical_product p LEFT JOIN product_capability c ON c.product_code = p.product_code ORDER BY p.created_at DESC")
        .query(ProductResponse.class).list();
  }

  @PostMapping("/products")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public void createProduct(@Valid @RequestBody ProductRequest request, Authentication authentication) {
    var now = Timestamp.from(Instant.now());
    jdbcClient.sql("INSERT INTO logical_product (product_code, name, status, created_at, updated_at) VALUES (:code, :name, 'ACTIVE', :now, :now)")
        .param("code", request.productCode()).param("name", request.name()).param("now", now).update();
    jdbcClient.sql("INSERT INTO product_capability (capability_id, product_code, country, currency, payment_method, min_amount, max_amount, supports_refund, status) VALUES (:id, :code, :country, :currency, :method, :min, :max, :refund, 'ACTIVE')")
        .param("id", UUID.randomUUID().toString()).param("code", request.productCode()).param("country", request.country())
        .param("currency", request.currency()).param("method", request.paymentMethod()).param("min", request.minAmount())
        .param("max", request.maxAmount()).param("refund", request.supportsRefund()).update();
    audit(authentication.getName(), "CREATE", "PRODUCT", request.productCode(), request);
  }

  @PutMapping("/products/{productCode}/status")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public void updateProductStatus(@PathVariable String productCode, @Valid @RequestBody StatusRequest request, Authentication authentication) {
    updateStatus("logical_product", "product_code", productCode, request.status());
    audit(authentication.getName(), "CHANGE_STATUS", "PRODUCT", productCode, request);
  }

  @GetMapping("/channels")
  public List<ChannelResponse> channels() {
    return jdbcClient.sql("SELECT channel_id, name, provider, status, weight, config_json FROM channel ORDER BY created_at DESC")
        .query((rs, rowNum) -> new ChannelResponse(rs.getString("channel_id"), rs.getString("name"), rs.getString("provider"),
            rs.getString("status"), rs.getInt("weight"), readMap(rs.getString("config_json")))).list();
  }

  @PostMapping("/channels")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public void createChannel(@Valid @RequestBody ChannelRequest request, Authentication authentication) {
    var now = Timestamp.from(Instant.now());
    jdbcClient.sql("INSERT INTO channel (channel_id, name, provider, status, weight, config_json, created_at, updated_at) VALUES (:id, :name, :provider, 'ACTIVE', :weight, :config, :now, :now)")
        .param("id", request.channelId()).param("name", request.name()).param("provider", request.provider())
        .param("weight", request.weight()).param("config", json(request.configuration())).param("now", now).update();
    jdbcClient.sql("INSERT INTO channel_capability (capability_id, channel_id, country, currency, payment_method, min_amount, max_amount, status) VALUES (:id, :channel, :country, :currency, :method, :min, :max, 'ACTIVE')")
        .param("id", UUID.randomUUID().toString()).param("channel", request.channelId()).param("country", request.country())
        .param("currency", request.currency()).param("method", request.paymentMethod()).param("min", request.minAmount()).param("max", request.maxAmount()).update();
    audit(authentication.getName(), "CREATE", "CHANNEL", request.channelId(), request);
  }

  @PutMapping("/channels/{channelId}/status")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public void updateChannelStatus(@PathVariable String channelId, @Valid @RequestBody StatusRequest request, Authentication authentication) {
    updateStatus("channel", "channel_id", channelId, request.status());
    audit(authentication.getName(), "CHANGE_STATUS", "CHANNEL", channelId, request);
  }

  @GetMapping("/routing-rules")
  public List<RoutingRuleResponse> routingRules() {
    return jdbcClient.sql("SELECT rule_id, release_version, product_code, merchant_id, payment_method, country, currency, channel_id, priority, weight, status FROM routing_rule ORDER BY release_version DESC, priority")
        .query(RoutingRuleResponse.class).list();
  }

  @PostMapping("/routing-rules")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public void createRoutingRule(@Valid @RequestBody RoutingRuleRequest request, Authentication authentication) {
    var version = draftVersion(request.releaseId());
    jdbcClient.sql("INSERT INTO routing_rule (rule_id, release_version, product_code, merchant_id, payment_method, country, currency, channel_id, priority, weight, status) VALUES (:id, :version, :product, :merchant, :method, :country, :currency, :channel, :priority, :weight, 'ACTIVE')")
        .param("id", request.ruleId()).param("version", version).param("product", request.productCode()).param("merchant", request.merchantId())
        .param("method", request.paymentMethod()).param("country", request.country()).param("currency", request.currency())
        .param("channel", request.channelId()).param("priority", request.priority()).param("weight", request.weight()).update();
    audit(authentication.getName(), "CREATE", "ROUTING_RULE", request.ruleId(), request);
  }

  @GetMapping("/pricing-rules")
  public List<PricingRuleResponse> pricingRules() {
    return jdbcClient.sql("SELECT rule_id, release_version, product_code, merchant_id, currency, fee_rate, fixed_fee, fee_mode, min_amount, max_amount, status FROM pricing_rule ORDER BY release_version DESC, id")
        .query(PricingRuleResponse.class).list();
  }

  @PostMapping("/pricing-rules")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'FINANCE')")
  @Transactional
  public void createPricingRule(@Valid @RequestBody PricingRuleRequest request, Authentication authentication) {
    var version = draftVersion(request.releaseId());
    jdbcClient.sql("INSERT INTO pricing_rule (rule_id, release_version, product_code, merchant_id, currency, fee_rate, fixed_fee, fee_mode, min_amount, max_amount, status) VALUES (:id, :version, :product, :merchant, :currency, :rate, :fixed, :mode, :min, :max, 'ACTIVE')")
        .param("id", request.ruleId()).param("version", version).param("product", request.productCode()).param("merchant", request.merchantId())
        .param("currency", request.currency()).param("rate", request.feeRate()).param("fixed", request.fixedFee()).param("mode", request.feeMode())
        .param("min", request.minAmount()).param("max", request.maxAmount()).update();
    audit(authentication.getName(), "CREATE", "PRICING_RULE", request.ruleId(), request);
  }

  @GetMapping("/risk-policies")
  public List<RiskPolicyResponse> riskPolicies() {
    return jdbcClient.sql("SELECT policy_id, release_version, name, priority, decision, condition_json, status FROM risk_policy ORDER BY release_version DESC, priority")
        .query((rs, rowNum) -> new RiskPolicyResponse(rs.getString("policy_id"), rs.getLong("release_version"), rs.getString("name"),
            rs.getInt("priority"), rs.getString("decision"), readMap(rs.getString("condition_json")), rs.getString("status"))).list();
  }

  @PostMapping("/risk-policies")
  @PreAuthorize("hasAnyRole('ADMIN', 'RISK')")
  @Transactional
  public void createRiskPolicy(@Valid @RequestBody RiskPolicyRequest request, Authentication authentication) {
    var version = draftVersion(request.releaseId());
    jdbcClient.sql("INSERT INTO risk_policy (policy_id, release_version, name, priority, decision, condition_json, status) VALUES (:id, :version, :name, :priority, :decision, :condition, 'ACTIVE')")
        .param("id", request.policyId()).param("version", version).param("name", request.name()).param("priority", request.priority())
        .param("decision", request.decision()).param("condition", json(request.condition())).update();
    audit(authentication.getName(), "CREATE", "RISK_POLICY", request.policyId(), request);
  }

  private long draftVersion(String releaseId) {
    return jdbcClient.sql("SELECT version_no FROM config_release WHERE release_id = :releaseId AND status = 'DRAFT'")
        .param("releaseId", releaseId).query(Long.class).single();
  }

  private long count(String table, String condition) {
    return jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE " + condition).query(Long.class).single();
  }

  private void updateStatus(String table, String idColumn, String id, String status) {
    jdbcClient.sql("UPDATE " + table + " SET status = :status, updated_at = :now WHERE " + idColumn + " = :id")
        .param("status", status).param("now", Timestamp.from(Instant.now())).param("id", id).update();
  }

  private void audit(String operator, String action, String type, String id, Object after) {
    jdbcClient.sql("INSERT INTO operation_audit (audit_id, operator_id, action, resource_type, resource_id, after_summary, created_at) VALUES (:audit, :operator, :action, :type, :id, :after, :now)")
        .param("audit", UUID.randomUUID().toString()).param("operator", operator).param("action", action).param("type", type)
        .param("id", id).param("after", json(after)).param("now", Timestamp.from(Instant.now())).update();
  }

  private String json(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (JsonProcessingException exception) { throw new IllegalArgumentException("内容不是合法 JSON", exception); }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readMap(String value) {
    try { return objectMapper.readValue(value, Map.class); }
    catch (JsonProcessingException exception) { throw new IllegalStateException("数据库 JSON 无法解析", exception); }
  }

  public record MerchantRequest(@NotBlank String merchantId, @NotBlank String name, @Pattern(regexp = "[A-Z]{3}") String settlementCurrency) {}
  public record MerchantResponse(String merchantId, String name, String status, String settlementCurrency, Instant createdAt, Instant updatedAt) {}
  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}
  public record ProductRequest(@NotBlank String productCode, @NotBlank String name, @NotBlank String country, @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethod, @DecimalMin("0.01") BigDecimal minAmount, @Positive BigDecimal maxAmount, boolean supportsRefund) {}
  public record ProductResponse(String productCode, String name, String status, String paymentMethod, String country, String currency,
      BigDecimal minAmount, BigDecimal maxAmount, Boolean supportsRefund) {}
  public record ChannelRequest(@NotBlank String channelId, @NotBlank String name, @NotBlank String provider, @NotNull Integer weight,
      @NotNull Map<String, Object> configuration, @NotBlank String country, @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethod, @DecimalMin("0.01") BigDecimal minAmount, @Positive BigDecimal maxAmount) {}
  public record ChannelResponse(String channelId, String name, String provider, String status, int weight, Map<String, Object> configuration) {}
  public record RoutingRuleRequest(@NotBlank String ruleId, @NotBlank String releaseId, @NotBlank String productCode, String merchantId,
      @NotBlank String paymentMethod, String country, @Pattern(regexp = "[A-Z]{3}") String currency, @NotBlank String channelId,
      @Positive int priority, @Positive int weight) {}
  public record RoutingRuleResponse(String ruleId, long releaseVersion, String productCode, String merchantId, String paymentMethod,
      String country, String currency, String channelId, int priority, int weight, String status) {}
  public record PricingRuleRequest(@NotBlank String ruleId, @NotBlank String releaseId, @NotBlank String productCode, String merchantId,
      @Pattern(regexp = "[A-Z]{3}") String currency, @DecimalMin("0.000000") BigDecimal feeRate, @DecimalMin("0.00") BigDecimal fixedFee,
      @Pattern(regexp = "INCLUSIVE|EXCLUSIVE") String feeMode, @DecimalMin("0.01") BigDecimal minAmount, @Positive BigDecimal maxAmount) {}
  public record PricingRuleResponse(String ruleId, long releaseVersion, String productCode, String merchantId, String currency,
      BigDecimal feeRate, BigDecimal fixedFee, String feeMode, BigDecimal minAmount, BigDecimal maxAmount, String status) {}
  public record RiskPolicyRequest(@NotBlank String policyId, @NotBlank String releaseId, @NotBlank String name, @Positive int priority,
      @Pattern(regexp = "PASS|REJECT|REVIEW") String decision, @NotNull Map<String, Object> condition) {}
  public record RiskPolicyResponse(String policyId, long releaseVersion, String name, int priority, String decision,
      Map<String, Object> condition, String status) {}
}
