package com.example.payments.trade.service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** PayProo collect protocol. Country, methods, URLs, and credentials are channel JSON data. */
@Component
public class PayprooChannelAdapter implements PaymentChannelAdapter {
  static final String SIGNATURE_PROFILE = "PAYPROO_RSA_SHA256_V1";
  private final ObjectMapper objectMapper;

  public PayprooChannelAdapter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String provider() {
    return "PAYPROO";
  }

  @Override
  public boolean supportsSignatureProfile(String signatureProfile) {
    return SIGNATURE_PROFILE.equalsIgnoreCase(signatureProfile);
  }

  @Override
  public boolean ownsRequestSignature() {
    return true;
  }

  @Override
  public PaymentChannelResult createPayment(PaymentChannelRequest request) {
    validateCreate(request);
    var payload = createPayload(request);
    payload.put("sign", sign(payload, request.runtime()));
    var raw = post(request.runtime().endpoint("create"), payload, request.runtime());
    var response = response(raw);
    if (!"1".equals(text(response.get("status")))) {
      return new PaymentChannelResult(
          request.orderId(), "FAILED", raw, text(response.get("error")), null, null);
    }
    var data = object(response.get("data"), "PayProo 下单响应缺少 data");
    verify(data, request.runtime());
    verifyOrderId(data, request.orderId());
    var orderNo = required(data, "orderNo");
    var paymentUrl = optional(data, "payUrl");
    if ("true".equalsIgnoreCase(request.runtime().setting("requirePayUrl")) && paymentUrl == null) {
      throw new IllegalArgumentException("PayProo 下单响应缺少 payUrl");
    }
    return new PaymentChannelResult(
        orderNo, "PROCESSING", raw, null, paymentUrl, optional(data, "qrCode"));
  }

  @Override
  public void validateCreate(PaymentChannelRequest request) {
    validateAmount(request.amount(), request.runtime());
    // Build the payload before persisting an attempt so provider-required payer data fails cleanly.
    createPayload(request);
  }

  @Override
  public PaymentChannelResult queryPayment(PaymentChannelQuery request) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("appId", appId(request.runtime()));
    payload.put("orderId", request.orderId());
    if (request.channelOrderId() != null
        && !request.channelOrderId().isBlank()
        && !request.channelOrderId().startsWith("pending:")) {
      payload.put("orderNo", request.channelOrderId());
    }
    payload.put("sign", sign(payload, request.runtime()));
    var raw = post(request.runtime().endpoint("query"), payload, request.runtime());
    var response = response(raw);
    if (!"1".equals(text(response.get("status")))) {
      return new PaymentChannelResult(
          request.channelOrderId(), "UNKNOWN", raw, text(response.get("error")), null, null);
    }
    var data = object(response.get("data"), "PayProo 查询响应缺少 data");
    verify(data, request.runtime());
    verifyExpected(data, request.orderId(), request.currency(), request.expectedAmount());
    if (request.channelOrderId() != null
        && !request.channelOrderId().startsWith("pending:")
        && !request.channelOrderId().equals(required(data, "orderNo"))) {
      throw new IllegalArgumentException("PayProo 查询渠道订单号不匹配");
    }
    return new PaymentChannelResult(
        required(data, "orderNo"),
        switch (required(data, "status")) {
          case "0", "1" -> "PROCESSING";
          case "2" -> "SUCCESS";
          case "3" -> "FAILED";
          case "4", "5" -> "UNKNOWN";
          default -> "UNKNOWN";
        },
        raw,
        optional(data, "errorMsg"),
        null,
        null);
  }

  @Override
  public PaymentChannelResult cancelPayment(PaymentChannelQuery request) {
    throw new UnsupportedOperationException("PayProo 未提供取消支付接口合同");
  }

  @Override
  public PaymentRefundResult refundPayment(PaymentRefundRequest request) {
    throw new UnsupportedOperationException("PayProo 退款接口合同尚未配置");
  }

  @Override
  public String callbackChannelOrderId(String rawPayload) {
    return required(response(rawPayload), "orderNo");
  }

  @Override
  public String callbackMerchantOrderId(String rawPayload) {
    return required(response(rawPayload), "orderId");
  }

  @Override
  public PaymentCallback verifyCallback(PaymentCallbackRequest request) {
    var payload = response(request.rawPayload());
    verify(payload, request.runtime());
    String status = required(payload, "status");
    if (!"2".equals(status) && !"3".equals(status)) {
      throw new IllegalArgumentException("PayProo 回调状态无效: " + status);
    }
    String expectedAppId = String.valueOf(appId(request.runtime()));
    if (!expectedAppId.equals(required(payload, "appId"))) {
      throw new IllegalArgumentException("PayProo 回调 appId 不匹配");
    }
    verifyExpected(
        payload, request.expectedOrderId(), request.expectedCurrency(), request.expectedAmount());
    return new PaymentCallback(
        request.callbackId(),
        required(payload, "orderNo"),
        "2".equals(status) ? "SUCCESS" : "FAILED",
        request.rawPayload());
  }

  Map<String, Object> createPayload(PaymentChannelRequest request) {
    var values = new LinkedHashMap<String, Object>();
    values.put("appId", appId(request.runtime()));
    values.put("orderId", request.orderId());
    values.put("amount", request.amount());
    values.put("callBackUrl", requiredValue(request.callbackUrl(), "callBackUrl"));
    values.put("subject", requiredValue(request.subject(), "subject"));
    values.putAll(request.payer());
    var method = method(request.paymentMethod(), request.runtime());
    values.putAll(method.values());
    var fields = csv(request.runtime().setting("requestFields"));
    if (fields.isEmpty()) throw new IllegalStateException("PayProo 渠道未配置 requestFields");
    var payload = new LinkedHashMap<String, Object>();
    for (String field : fields) {
      Object value = values.get(field);
      if (value != null && !String.valueOf(value).isBlank()) payload.put(field, value);
    }
    var required = new java.util.ArrayList<>(csv(request.runtime().setting("requiredFields")));
    required.addAll(method.requiredFields());
    for (String field : required) {
      if (!payload.containsKey(field))
        throw new IllegalArgumentException("PayProo 缺少必填字段: " + field);
    }
    return payload;
  }

  private MethodMapping method(String paymentMethod, ChannelRuntimeContext runtime) {
    Object rawMappings = runtime.settings().get("methodMappings");
    if (!(rawMappings instanceof Map<?, ?> mappings)) {
      throw new IllegalStateException("PayProo 渠道未配置 methodMappings");
    }
    Object raw = mappings.get(paymentMethod);
    if (!(raw instanceof Map<?, ?> source)) {
      throw new IllegalArgumentException("PayProo 渠道未开通支付方式: " + paymentMethod);
    }
    var values = new LinkedHashMap<String, Object>();
    source.forEach((key, value) -> values.put(String.valueOf(key), value));
    var required = list(values.remove("requiredFields"));
    if (optional(values, "payType") == null || optional(values, "payModel") == null) {
      throw new IllegalStateException("PayProo 支付方式映射缺少 payType 或 payModel");
    }
    return new MethodMapping(Map.copyOf(values), required);
  }

  String sign(Map<String, ?> payload, ChannelRuntimeContext runtime) {
    String canonical = canonical(payload) + "&key=" + secret(runtime, "merchantSecretKey");
    try {
      var privateKey =
          KeyFactory.getInstance("RSA")
              .generatePrivate(new PKCS8EncodedKeySpec(key(secret(runtime, "merchantPrivateKey"))));
      var signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(privateKey);
      signature.update(canonical.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (Exception exception) {
      throw new IllegalArgumentException("PayProo 商户私钥无效", exception);
    }
  }

  void verify(Map<String, ?> payload, ChannelRuntimeContext runtime) {
    String sign = required(payload, "sign");
    String canonical = canonical(payload) + "&key=" + secret(runtime, "merchantSecretKey");
    try {
      var publicKey =
          KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(key(secret(runtime, "platformPublicKey"))));
      var verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(publicKey);
      verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
      if (!verifier.verify(Base64.getDecoder().decode(sign))) {
        throw new IllegalArgumentException("PayProo 响应验签失败");
      }
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("PayProo 平台公钥无效", exception);
    }
  }

  static String canonical(Map<String, ?> values) {
    var sorted = new TreeMap<String, String>();
    values.forEach(
        (key, value) -> {
          if (value != null
              && !String.valueOf(value).isBlank()
              && !"sign".equals(key)
              && !"sign_type".equals(key)) {
            sorted.put(key, String.valueOf(value));
          }
        });
    if (sorted.isEmpty()) throw new IllegalArgumentException("PayProo 签名字段为空");
    return sorted.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + "&" + right)
        .orElseThrow();
  }

  private Map<String, Object> response(String raw) {
    try {
      return objectMapper.readValue(raw, Map.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("PayProo 返回非 JSON 数据", exception);
    }
  }

  private void verifyExpected(
      Map<String, ?> payload,
      String expectedOrderId,
      String expectedCurrency,
      String expectedAmount) {
    verifyOrderId(payload, expectedOrderId);
    if (!expectedCurrency.equalsIgnoreCase(required(payload, "currency"))) {
      throw new IllegalArgumentException("PayProo 返回币种不匹配");
    }
    try {
      if (new BigDecimal(expectedAmount).compareTo(new BigDecimal(required(payload, "amount")))
          != 0) {
        throw new IllegalArgumentException("PayProo 返回金额不匹配");
      }
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("PayProo 返回金额无效", exception);
    }
  }

  private static void verifyOrderId(Map<String, ?> payload, String expectedOrderId) {
    if (!expectedOrderId.equals(required(payload, "orderId"))) {
      throw new IllegalArgumentException("PayProo 返回商户订单号不匹配");
    }
  }

  private String post(String endpoint, Map<String, Object> payload, ChannelRuntimeContext runtime) {
    URI uri;
    try {
      uri = URI.create(endpoint);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("PayProo 请求地址无效", exception);
    }
    if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      throw new IllegalStateException("PayProo 请求地址必须为 HTTPS");
    }
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeout(runtime, "connectTimeoutMs", 3000));
    factory.setReadTimeout(timeout(runtime, "readTimeoutMs", 10000));
    try {
      return RestClient.builder()
          .requestFactory(factory)
          .build()
          .post()
          .uri(uri)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(String.class);
    } catch (RestClientException exception) {
      throw new ChannelRequestAmbiguousException("PayProo 请求结果不确定", exception);
    }
  }

  private static int timeout(ChannelRuntimeContext runtime, String key, int fallback) {
    String configured = runtime.setting(key);
    if (configured.isBlank()) return fallback;
    try {
      int value = Integer.parseInt(configured);
      if (value < 100 || value > 120_000) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("PayProo " + key + " 必须在 100 到 120000 之间", exception);
    }
  }

  private Object appId(ChannelRuntimeContext runtime) {
    String appId = runtime.setting("appId");
    if (appId.isBlank()) throw new IllegalStateException("PayProo 渠道未配置 appId");
    try {
      return Long.parseLong(appId);
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("PayProo appId 无效", exception);
    }
  }

  private void validateAmount(String amount, ChannelRuntimeContext runtime) {
    BigDecimal value;
    try {
      value = new BigDecimal(amount);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("PayProo 金额无效", exception);
    }
    if (value.signum() <= 0) throw new IllegalArgumentException("PayProo 金额必须大于零");
    int scale;
    try {
      scale = Integer.parseInt(runtime.setting("amountScale"));
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("PayProo 渠道未配置 amountScale", exception);
    }
    if (value.stripTrailingZeros().scale() > scale) {
      throw new IllegalArgumentException("PayProo 金额小数位超出渠道配置");
    }
    if ("true".equalsIgnoreCase(runtime.setting("integerAmount"))
        && value.stripTrailingZeros().scale() > 0) {
      throw new IllegalArgumentException("PayProo 渠道仅接受整数金额");
    }
  }

  private static Map<String, Object> object(Object value, String message) {
    if (!(value instanceof Map<?, ?> source)) throw new IllegalArgumentException(message);
    var result = new LinkedHashMap<String, Object>();
    source.forEach((key, item) -> result.put(String.valueOf(key), item));
    return result;
  }

  private static java.util.List<String> csv(String value) {
    if (value == null || value.isBlank()) return java.util.List.of();
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .toList();
  }

  private static java.util.List<String> list(Object value) {
    if (!(value instanceof java.util.Collection<?> values)) return java.util.List.of();
    return values.stream()
        .map(String::valueOf)
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .toList();
  }

  private static String secret(ChannelRuntimeContext runtime, String key) {
    return runtime.secret(key).orElseThrow(() -> new IllegalStateException("PayProo 未配置 " + key));
  }

  private static String required(Map<String, ?> values, String key) {
    String value = optional(values, key);
    if (value == null) throw new IllegalArgumentException("PayProo 缺少字段: " + key);
    return value;
  }

  private static String requiredValue(String value, String key) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("PayProo 缺少字段: " + key);
    return value;
  }

  private static String optional(Map<String, ?> values, String key) {
    Object value = values.get(key);
    return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private static byte[] key(String value) {
    return Base64.getDecoder()
        .decode(
            value
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", ""));
  }

  private record MethodMapping(Map<String, Object> values, java.util.List<String> requiredFields) {}
}
