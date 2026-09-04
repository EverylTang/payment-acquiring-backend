package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.ConfigurationSnapshotMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ConfigurationSnapshotService {
  private final ConfigurationSnapshotMapper mapper;

  public Map<String, Object> snapshot(
      String merchantId,
      String productCode,
      String paymentMethod,
      String country,
      String currency,
      BigDecimal amount) {
    var version =
        java.util.Optional.ofNullable(mapper.selectLatestPublishedVersion())
            .orElseThrow(() -> unavailable("没有已发布的配置版本"));
    requireAvailable(mapper.countActiveMerchant(merchantId), "商户不可用");
    requireAvailable(mapper.countActiveProduct(productCode), "产品不可用");
    requireBinding(merchantId, productCode);

    var product =
        java.util.Optional.ofNullable(
                mapper.selectProductCapability(productCode, paymentMethod, amount))
            .orElseThrow(() -> unavailable("产品能力不支持当前交易"));

    var channelPaymentMethod = product.channelPaymentMethod();

    var candidates =
        mapper.selectChannelCandidates(
            version, productCode, merchantId, channelPaymentMethod, country, currency, amount);
    if (candidates.isEmpty()) throw unavailable("没有可用支付渠道");
    var selectedChannel = selectWeightedCandidate(candidates);
    var channelRuntime =
        java.util.Optional.ofNullable(mapper.selectChannelRuntime(selectedChannel.channelId()))
            .orElseThrow(() -> unavailable("渠道运行配置不可用"));

    var pricing =
        java.util.Optional.ofNullable(
                mapper.selectPricing(
                    version,
                    productCode,
                    merchantId,
                    selectedChannel.channelId(),
                    currency,
                    amount))
            .orElseThrow(() -> unavailable("没有匹配的费率规则"));

    var risk =
        java.util.Optional.ofNullable(mapper.selectRiskPolicy(version, productCode, currency))
            .orElse(RiskPolicy.pass());

    var result = new LinkedHashMap<String, Object>();
    result.put("merchantId", merchantId);
    result.put("productCode", productCode);
    result.put("paymentMethod", paymentMethod);
    result.put("channelPaymentMethod", channelPaymentMethod);
    result.put("country", country);
    result.put("currency", currency);
    result.put("amount", amount);
    result.put("configVersion", version);
    result.put("product", product.asMap());
    result.put(
        "route",
        selectedChannel.asMap(
            channelRuntime,
            mapper.selectActiveChannelCredentialBindings(selectedChannel.channelId())));
    result.put("pricing", pricing.asMap());
    result.put("risk", risk.asMap());
    result.put("candidates", candidates.stream().map(ChannelCandidate::channelId).toList());
    return result;
  }

  public List<String> validate(long version) {
    var errors = new java.util.ArrayList<String>();
    if (mapper.countActiveRoutingRules(version) == 0) errors.add("至少需要一条路由规则");
    if (mapper.countActivePricingRules(version) == 0) errors.add("至少需要一条费率规则");
    if (mapper.countActiveRiskPolicies(version) == 0) errors.add("至少需要一条风控策略");
    if (mapper.countInactiveRoutingChannels(version) > 0) errors.add("路由包含不存在或已停用的渠道");
    if (mapper.countInvalidPricingRules(version) > 0) errors.add("费率或金额区间不合法");
    if (mapper.countConflictingRoutingRules(version) > 0) errors.add("路由规则存在相同作用域和优先级冲突");
    return errors;
  }

  public Map<String, Object> channelRuntime(String channelId) {
    var runtime =
        java.util.Optional.ofNullable(mapper.selectChannelRuntime(channelId))
            .orElseThrow(() -> unavailable("渠道运行配置不可用"));
    return channelRuntime(runtime, mapper.selectActiveChannelCredentialBindings(channelId));
  }

  private void requireAvailable(long count, String message) {
    if (count == 0) throw unavailable(message);
  }

  private void requireBinding(String merchantId, String productCode) {
    var count = mapper.countActiveMerchantProduct(merchantId, productCode);
    if (count == 0) throw unavailable("商户未开通当前产品");
  }

  private ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }

  private static Map<String, Object> channelRuntime(
      ChannelRuntime runtime, List<CredentialBinding> bindings) {
    return Map.of(
        "channelId", runtime.channelId(),
        "provider", runtime.provider(),
        "requestUrl", runtime.requestUrl(),
        "signatureProfile", runtime.signatureProfile(),
        "settings", runtime.settings(),
        "credentialBindings", bindings.stream().map(CredentialBinding::asMap).toList());
  }

  private ChannelCandidate selectWeightedCandidate(List<ChannelCandidate> candidates) {
    return selectWeightedCandidate(candidates, ThreadLocalRandom.current().nextLong());
  }

  static ChannelCandidate selectWeightedCandidate(List<ChannelCandidate> candidates, long ticket) {
    var first = candidates.getFirst();
    var tier =
        candidates.stream()
            .filter(candidate -> candidate.scopeRank() == first.scopeRank())
            .filter(candidate -> candidate.priority() == first.priority())
            .toList();
    long totalWeight = tier.stream().mapToLong(ChannelCandidate::weight).sum();
    if (totalWeight <= 0) return tier.getFirst();
    long selectedTicket = Math.floorMod(ticket, totalWeight);
    long accumulated = 0;
    for (var candidate : tier) {
      accumulated += candidate.weight();
      if (selectedTicket < accumulated) return candidate;
    }
    return tier.getLast();
  }

  public record ProductCapability(
      String channelPaymentMethod,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      boolean supportsRefund) {
    Map<String, Object> asMap() {
      return Map.of(
          "enabled",
          true,
          "channelPaymentMethod",
          channelPaymentMethod,
          "supportsRefund",
          supportsRefund,
          "minAmount",
          minAmount,
          "maxAmount",
          maxAmount);
    }
  }

  public record ChannelCandidate(String channelId, int scopeRank, int priority, int weight) {
    Map<String, Object> asMap(ChannelRuntime runtime, List<CredentialBinding> bindings) {
      var route = new LinkedHashMap<>(channelRuntime(runtime, bindings));
      route.put("priority", priority);
      route.put("weight", weight);
      return route;
    }
  }

  public record ChannelRuntime(
      String channelId,
      String provider,
      String requestUrl,
      String signatureProfile,
      String configuration) {
    Map<String, Object> settings() {
      try {
        return new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(configuration, Map.class);
      } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
        throw new IllegalStateException("渠道运行参数不是合法 JSON", exception);
      }
    }
  }

  public record CredentialBinding(String credentialRole, String secretRef, String keyVersion) {
    Map<String, String> asMap() {
      return Map.of(
          "role",
          credentialRole,
          "secretRef",
          secretRef,
          "keyVersion",
          keyVersion == null ? "" : keyVersion);
    }
  }

  public record Pricing(
      String ruleId,
      BigDecimal feeRate,
      BigDecimal fixedFee,
      BigDecimal extraFee,
      BigDecimal minFee,
      BigDecimal maxFee,
      String feeType,
      String tieredFees,
      String mode) {
    Map<String, Object> asMap() {
      var pricing = new LinkedHashMap<String, Object>();
      pricing.put("ruleId", ruleId);
      pricing.put("feeRate", feeRate);
      pricing.put("fixedFee", fixedFee);
      pricing.put("extraFee", extraFee == null ? BigDecimal.ZERO : extraFee);
      pricing.put("minFee", minFee);
      pricing.put("maxFee", maxFee);
      pricing.put(
          "feeType", feeType == null || feeType.isBlank() ? PricingFeeRules.COMBINED : feeType);
      pricing.put("tiers", tieredFees == null ? "[]" : tieredFees);
      pricing.put("mode", mode);
      pricing.put("scale", 2);
      return pricing;
    }
  }

  public record RiskPolicy(String policyId, String decision) {
    static RiskPolicy pass() {
      return new RiskPolicy(null, "PASS");
    }

    Map<String, Object> asMap() {
      return policyId == null
          ? Map.of("decision", decision)
          : Map.of("policyId", policyId, "decision", decision);
    }
  }
}
