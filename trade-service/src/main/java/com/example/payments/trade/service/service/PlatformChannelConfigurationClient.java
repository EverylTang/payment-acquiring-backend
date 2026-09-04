package com.example.payments.trade.service.service;

import com.example.payments.trade.service.domain.PaymentOrder;
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
      return runtime(route);
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

  private ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
