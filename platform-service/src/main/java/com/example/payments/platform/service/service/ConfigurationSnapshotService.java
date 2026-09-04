package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.ConfigurationSnapshotMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        java.util.Optional.ofNullable(mapper.selectProductCapability(productCode, paymentMethod, amount))
            .orElseThrow(() -> unavailable("产品能力不支持当前交易"));

    var channelPaymentMethod = product.channelPaymentMethod();

    var candidates =
        mapper.selectChannelCandidates(
            version, productCode, merchantId, channelPaymentMethod, country, currency, amount);
    if (candidates.isEmpty()) throw unavailable("没有可用支付渠道");

    var pricing =
        java.util.Optional.ofNullable(mapper.selectPricing(version, productCode, merchantId, currency, amount))
            .orElseThrow(() -> unavailable("没有匹配的费率规则"));

    var risk = java.util.Optional.ofNullable(mapper.selectRiskPolicy(version, productCode, currency)).orElse(RiskPolicy.pass());

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
    result.put("route", candidates.getFirst().asMap());
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

  public record ProductCapability(
      String channelPaymentMethod, BigDecimal minAmount, BigDecimal maxAmount, boolean supportsRefund) {
    Map<String, Object> asMap() {
      return Map.of("enabled", true, "channelPaymentMethod", channelPaymentMethod, "supportsRefund", supportsRefund, "minAmount", minAmount, "maxAmount", maxAmount);
    }
  }

  public record ChannelCandidate(String channelId, int priority, int weight) {
    Map<String, Object> asMap() {
      return Map.of("channelId", channelId, "priority", priority, "weight", weight);
    }
  }

  public record Pricing(String ruleId, BigDecimal feeRate, BigDecimal fixedFee, String mode) {
    Map<String, Object> asMap() {
      return Map.of("ruleId", ruleId, "feeRate", feeRate, "fixedFee", fixedFee, "mode", mode, "scale", 2);
    }
  }

  public record RiskPolicy(String policyId, String decision) {
    static RiskPolicy pass() {
      return new RiskPolicy(null, "PASS");
    }

    Map<String, Object> asMap() {
      return policyId == null ? Map.of("decision", decision) : Map.of("policyId", policyId, "decision", decision);
    }
  }
}
