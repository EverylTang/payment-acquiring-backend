package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.PricingRuleMapper;
import com.example.payments.platform.service.model.PricingRuleFull;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PricingRuleAdminService {
  private final PricingRuleMapper mapper;
  private final OperationAuditService auditService;
  private final ObjectMapper objectMapper;

  public AdminPageResponse<PricingRuleResponse> list(int page, int pageSize) {
    page = Math.max(page, 1);
    pageSize = Math.clamp(pageSize, 1, 100);
    int offset = (page - 1) * pageSize;
    List<PricingRuleFull> rules = mapper.selectByPage(offset, pageSize);
    int total = mapper.countAll();

    List<PricingRuleResponse> items =
        rules.stream()
            .map(
                r ->
                    new PricingRuleResponse(
                        r.getRuleId(),
                        r.getReleaseVersion(),
                        r.getProductCode(),
                        r.getMerchantId(),
                        r.getChannelId(),
                        r.getCurrency(),
                        r.getFeeRate(),
                        r.getFixedFee(),
                        r.getExtraFee(),
                        r.getMinFee(),
                        r.getMaxFee(),
                        r.getFeeType(),
                        readTiers(r.getTieredFees()),
                        r.getFeeMode(),
                        r.getMinAmount(),
                        r.getMaxAmount(),
                        r.getStatus()))
            .toList();

    return new AdminPageResponse<>(items, page, pageSize, total);
  }

  public PricingRuleResponse detail(String ruleId) {
    PricingRuleFull rule = mapper.selectByRuleId(ruleId);
    if (rule == null) {
      throw new IllegalArgumentException("Pricing rule not found: " + ruleId);
    }
    return new PricingRuleResponse(
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
        rule.getStatus());
  }

  @Transactional
  public PricingRuleResponse create(PricingRuleRequest request, Authentication auth) {
    PricingFeeRules.validate(
        request.feeType(),
        request.feeRate(),
        request.fixedFee(),
        request.extraFee(),
        request.minFee(),
        request.maxFee(),
        request.tiers());
    String ruleId = "PR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    PricingRuleFull rule = new PricingRuleFull();
    rule.setRuleId(ruleId);
    rule.setReleaseVersion(request.releaseVersion());
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

    mapper.insert(rule);
    audit(auth.getName(), "CREATE_PRICING_RULE", ruleId);

    return detail(ruleId);
  }

  @Transactional
  public PricingRuleResponse update(
      String ruleId, PricingRuleRequest request, Authentication auth) {
    PricingRuleFull existing = mapper.selectByRuleId(ruleId);
    if (existing == null) {
      throw new IllegalArgumentException("Pricing rule not found: " + ruleId);
    }
    PricingFeeRules.validate(
        request.feeType(),
        request.feeRate(),
        request.fixedFee(),
        request.extraFee(),
        request.minFee(),
        request.maxFee(),
        request.tiers());

    existing.setReleaseVersion(request.releaseVersion());
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

    mapper.update(existing);
    audit(auth.getName(), "UPDATE_PRICING_RULE", ruleId);

    return detail(ruleId);
  }

  @Transactional
  public void updateStatus(String ruleId, String status, Authentication auth) {
    PricingRuleFull existing = mapper.selectByRuleId(ruleId);
    if (existing == null) {
      throw new IllegalArgumentException("Pricing rule not found: " + ruleId);
    }

    mapper.updateStatus(ruleId, status);
    audit(auth.getName(), "UPDATE_PRICING_RULE_STATUS", ruleId);
  }

  @Transactional
  public void delete(String ruleId, Authentication auth) {
    PricingRuleFull existing = mapper.selectByRuleId(ruleId);
    if (existing == null) {
      throw new IllegalArgumentException("Pricing rule not found: " + ruleId);
    }

    mapper.deleteByRuleId(ruleId);
    audit(auth.getName(), "DELETE_PRICING_RULE", ruleId);
  }

  private void audit(String username, String action, String targetId) {
    auditService.record(username, action, "PRICING_RULE", targetId, null);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("阶梯手续费配置不是合法 JSON", exception);
    }
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

  public record PricingRuleRequest(
      @NotNull Long releaseVersion,
      @NotBlank String productCode,
      String merchantId,
      String channelId,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotNull @DecimalMin("0.000000") BigDecimal feeRate,
      @NotNull @DecimalMin("0.00") BigDecimal fixedFee,
      @NotNull @DecimalMin("0.00") BigDecimal extraFee,
      @DecimalMin("0.00") BigDecimal minFee,
      @DecimalMin("0.00") BigDecimal maxFee,
      @NotBlank @Pattern(regexp = "FIXED|PERCENTAGE|TIERED|COMBINED") String feeType,
      List<PricingFeeRules.FeeTier> tiers,
      @NotBlank @Pattern(regexp = "PAYER_BEAR|MERCHANT_BEAR|INCLUSIVE|EXCLUSIVE") String feeMode,
      @NotNull @DecimalMin("0.01") BigDecimal minAmount,
      @NotNull @DecimalMin("0.01") BigDecimal maxAmount) {}

  public record PricingRuleResponse(
      String ruleId,
      Long releaseVersion,
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

  public record StatusRequest(@NotBlank String status) {}
}
