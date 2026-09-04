package com.example.payments.trade.service.service;

import com.example.payments.trade.service.domain.PaymentOrder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PlatformChannelConfigurationClient {
  private final RestClient client;
  private final String internalToken;
  private final ChannelSecretResolver secretResolver;

  public PlatformChannelConfigurationClient(
      @Value("${trade.routing.platform-base-url:http://127.0.0.1:8081}") String platformBaseUrl,
      @Value("${trade.routing.internal-token:${GATEWAY_INTERNAL_TOKEN:}}") String internalToken,
      ChannelSecretResolver secretResolver) {
    this.client = RestClient.builder().baseUrl(platformBaseUrl).build();
    this.internalToken = internalToken;
    this.secretResolver = secretResolver;
  }

  public ChannelRuntimeContext resolve(PaymentOrder order) {
    try {
      var route =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readValue(order.routeSnapshot(), Map.class);
      var channelId = text(route.get("channelId"));
      if (!channelId.isBlank()) return resolve(channelId);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      // Orders created before configuration snapshots are resolved through the current route
      // lookup.
    }
    return resolveConfiguration(order).runtime();
  }

  public ResolvedPaymentConfiguration resolveConfiguration(PaymentOrder order) {
    try {
      var snapshot =
          client
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/api/internal/v1/configurations/snapshot")
                          .queryParam("merchantId", order.merchantId())
                          .queryParam("productCode", order.productCode())
                          .queryParam("paymentMethod", order.paymentMethod())
                          .queryParam("country", order.country())
                          .queryParam("currency", order.currency())
                          .queryParam("amount", order.amount().toPlainString())
                          .build())
              .headers(headers -> headers.set("X-Internal-Token", internalToken))
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      if (snapshot == null || !(snapshot.get("route") instanceof Map<?, ?> route)) {
        throw unavailable("平台未返回渠道路由");
      }
      if (!(snapshot.get("pricing") instanceof Map<?, ?> pricing)) {
        throw unavailable("平台未返回费率配置");
      }
      return new ResolvedPaymentConfiguration(
          runtime(route),
          text(pricing.get("ruleId")),
          decimal(pricing.get("feeRate"), "费率"),
          decimal(pricing.get("fixedFee"), "固定费用"),
          decimalOrZero(pricing.get("extraFee"), "额外手续费"),
          optionalDecimal(pricing.get("minFee"), "最小手续费"),
          optionalDecimal(pricing.get("maxFee"), "最大手续费"),
          text(pricing.get("feeType")),
          tiers(pricing.get("tiers")),
          text(pricing.get("mode")),
          text(snapshot.get("configVersion")));
    } catch (RestClientException exception) {
      throw unavailable("无法读取渠道运行配置");
    }
  }

  public ChannelRuntimeContext resolve(String channelId) {
    try {
      var runtime =
          client
              .get()
              .uri("/api/internal/v1/configurations/channels/{channelId}/runtime", channelId)
              .headers(headers -> headers.set("X-Internal-Token", internalToken))
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      if (runtime == null) throw unavailable("平台未返回渠道运行配置");
      return runtime(runtime);
    } catch (RestClientException exception) {
      throw unavailable("无法读取渠道运行配置");
    }
  }

  ChannelRuntimeContext fromSnapshot(Map<?, ?> snapshot) {
    return runtime(snapshot);
  }

  private ChannelRuntimeContext runtime(Map<?, ?> route) {
    var channelId = text(route.get("channelId"));
    var provider = text(route.get("provider"));
    var requestUrl = text(route.get("requestUrl"));
    var signatureProfile = text(route.get("signatureProfile"));
    if (channelId.isBlank()
        || provider.isBlank()
        || requestUrl.isBlank()
        || signatureProfile.isBlank()) {
      throw unavailable("渠道运行配置不完整");
    }
    return new ChannelRuntimeContext(
        channelId,
        provider,
        requestUrl,
        signatureProfile,
        map(route.get("settings")),
        credentialReferences(route.get("credentialBindings")),
        secretResolver);
  }

  private Map<String, ChannelCredentialReference> credentialReferences(Object value) {
    if (!(value instanceof List<?> bindings)) return Map.of();
    var references = new LinkedHashMap<String, ChannelCredentialReference>();
    for (var binding : bindings) {
      if (!(binding instanceof Map<?, ?> item)) continue;
      var role = text(item.get("role"));
      var reference = text(item.get("secretRef"));
      var keyVersion = text(item.get("keyVersion"));
      if (!role.isBlank() && !reference.isBlank()) {
        references.put(role, new ChannelCredentialReference(reference, keyVersion));
      }
    }
    return Map.copyOf(references);
  }

  private Map<String, Object> map(Object value) {
    if (!(value instanceof Map<?, ?> source)) return Map.of();
    var result = new LinkedHashMap<String, Object>();
    source.forEach((key, item) -> result.put(String.valueOf(key), item));
    return Map.copyOf(result);
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private BigDecimal decimal(Object value, String field) {
    try {
      var result = new BigDecimal(text(value));
      if (result.signum() < 0) throw new NumberFormatException();
      return result;
    } catch (NumberFormatException exception) {
      throw unavailable("渠道运行配置中的" + field + "无效");
    }
  }

  private BigDecimal decimalOrZero(Object value, String field) {
    return value == null || text(value).isBlank() ? BigDecimal.ZERO : decimal(value, field);
  }

  private BigDecimal optionalDecimal(Object value, String field) {
    return value == null || text(value).isBlank() ? null : decimal(value, field);
  }

  private List<FeeTier> tiers(Object value) {
    if (value == null || text(value).isBlank() || "[]".equals(text(value))) return List.of();
    try {
      Object parsed =
          value instanceof String
              ? new com.fasterxml.jackson.databind.ObjectMapper()
                  .readValue((String) value, List.class)
              : value;
      if (!(parsed instanceof List<?> source)) throw new IllegalArgumentException();
      var result = new ArrayList<FeeTier>();
      for (var item : source) {
        if (!(item instanceof Map<?, ?> tier)) throw new IllegalArgumentException();
        result.add(
            new FeeTier(
                decimal(tier.get("minAmount"), "阶梯最小金额"),
                decimal(tier.get("maxAmount"), "阶梯最大金额"),
                decimal(tier.get("feeRate"), "阶梯比例手续费"),
                decimal(tier.get("fixedFee"), "阶梯固定手续费")));
      }
      return List.copyOf(result);
    } catch (com.fasterxml.jackson.core.JsonProcessingException
        | IllegalArgumentException exception) {
      throw unavailable("渠道运行配置中的阶梯手续费无效");
    }
  }

  private ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }

  public record ResolvedPaymentConfiguration(
      ChannelRuntimeContext runtime,
      String pricingRuleId,
      BigDecimal feeRate,
      BigDecimal fixedFee,
      BigDecimal extraFee,
      BigDecimal minFee,
      BigDecimal maxFee,
      String feeType,
      List<FeeTier> tiers,
      String feeMode,
      String configVersion) {
    public ResolvedPaymentConfiguration {
      if (feeType == null || feeType.isBlank()) feeType = "COMBINED";
      if (!List.of("FIXED", "PERCENTAGE", "TIERED", "COMBINED").contains(feeType)) {
        throw new IllegalArgumentException("渠道运行配置中的手续费类型无效");
      }
      tiers = tiers == null ? List.of() : List.copyOf(tiers);
      extraFee = extraFee == null ? BigDecimal.ZERO : extraFee;
      if (extraFee.signum() < 0
          || (minFee != null && minFee.signum() < 0)
          || (maxFee != null && maxFee.signum() < 0)
          || (minFee != null && maxFee != null && minFee.compareTo(maxFee) > 0)) {
        throw new IllegalArgumentException("渠道运行配置中的手续费限制无效");
      }
      if ("TIERED".equals(feeType) && tiers.isEmpty()) {
        throw new IllegalArgumentException("渠道运行配置中的阶梯手续费不能为空");
      }
      if (!List.of("PAYER_BEAR", "MERCHANT_BEAR", "INCLUSIVE", "EXCLUSIVE").contains(feeMode)) {
        throw new IllegalArgumentException("渠道运行配置中的费率模式无效");
      }
    }

    public ResolvedPaymentConfiguration(
        ChannelRuntimeContext runtime,
        String pricingRuleId,
        BigDecimal feeRate,
        BigDecimal fixedFee,
        String feeMode,
        String configVersion) {
      this(
          runtime,
          pricingRuleId,
          feeRate,
          fixedFee,
          BigDecimal.ZERO,
          null,
          null,
          "COMBINED",
          List.of(),
          feeMode,
          configVersion);
    }

    public ResolvedPaymentConfiguration(
        ChannelRuntimeContext runtime,
        String pricingRuleId,
        BigDecimal feeRate,
        BigDecimal fixedFee,
        String feeType,
        List<FeeTier> tiers,
        String feeMode,
        String configVersion) {
      this(
          runtime,
          pricingRuleId,
          feeRate,
          fixedFee,
          BigDecimal.ZERO,
          null,
          null,
          feeType,
          tiers,
          feeMode,
          configVersion);
    }
  }

  public record FeeTier(
      BigDecimal minAmount, BigDecimal maxAmount, BigDecimal feeRate, BigDecimal fixedFee) {}
}
