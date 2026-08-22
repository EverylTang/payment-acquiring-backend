package com.example.payments.platform.service.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import com.example.payments.platform.service.infrastructure.persistence.MybatisPlusClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConfigurationSnapshotService {
  private final MybatisPlusClient mybatisClient;

  public ConfigurationSnapshotService(MybatisPlusClient mybatisClient) {
    this.mybatisClient = mybatisClient;
  }

  public Map<String, Object> snapshot(String merchantId, String productCode, String paymentMethod, String country,
      String currency, BigDecimal amount) {
    var version = mybatisClient.sql("SELECT version_no FROM config_release WHERE status = 'PUBLISHED' ORDER BY version_no DESC LIMIT 1")
        .query(Long.class).optional().orElseThrow(() -> unavailable("没有已发布的配置版本"));
    requireExists("SELECT COUNT(*) FROM merchant WHERE merchant_id = :value AND status = 'ACTIVE'", merchantId, "商户不可用");
    requireExists("SELECT COUNT(*) FROM logical_product WHERE product_code = :value AND status = 'ACTIVE'", productCode, "产品不可用");
    requireBinding(merchantId, productCode);

    var product = mybatisClient.sql("SELECT min_amount, max_amount, supports_refund FROM product_capability WHERE product_code = :product AND payment_method = :method AND country = :country AND currency = :currency AND status = 'ACTIVE' AND :amount BETWEEN min_amount AND max_amount")
        .param("product", productCode).param("method", paymentMethod).param("country", country).param("currency", currency).param("amount", amount)
        .query((rs, rowNum) -> Map.<String, Object>of("enabled", true, "supportsRefund", rs.getBoolean("supports_refund"),
            "minAmount", rs.getBigDecimal("min_amount"), "maxAmount", rs.getBigDecimal("max_amount")))
        .optional().orElseThrow(() -> unavailable("产品能力不支持当前交易"));

    var candidates = mybatisClient.sql("SELECT r.channel_id, r.priority, r.weight FROM routing_rule r JOIN channel c ON c.channel_id = r.channel_id AND c.status = 'ACTIVE' JOIN channel_capability cc ON cc.channel_id = r.channel_id AND cc.status = 'ACTIVE' WHERE r.release_version = :version AND r.product_code = :product AND (r.merchant_id = :merchant OR r.merchant_id IS NULL) AND r.payment_method = :method AND (r.country = :country OR r.country IS NULL) AND r.currency = :currency AND r.status = 'ACTIVE' AND cc.payment_method = :method AND cc.country = :country AND cc.currency = :currency AND :amount BETWEEN cc.min_amount AND cc.max_amount ORDER BY CASE WHEN r.merchant_id = :merchant THEN 0 ELSE 1 END, r.priority, r.weight DESC")
        .param("version", version).param("product", productCode).param("merchant", merchantId).param("method", paymentMethod)
        .param("country", country).param("currency", currency).param("amount", amount)
        .query((rs, rowNum) -> Map.<String, Object>of("channelId", rs.getString("channel_id"), "priority", rs.getInt("priority"), "weight", rs.getInt("weight")))
        .list();
    if (candidates.isEmpty()) throw unavailable("没有可用支付渠道");

    var pricing = mybatisClient.sql("SELECT fee_rate, fixed_fee, fee_mode, rule_id FROM pricing_rule WHERE release_version = :version AND product_code = :product AND (merchant_id = :merchant OR merchant_id IS NULL) AND currency = :currency AND status = 'ACTIVE' AND :amount BETWEEN min_amount AND max_amount ORDER BY CASE WHEN merchant_id = :merchant THEN 0 ELSE 1 END, id LIMIT 1")
        .param("version", version).param("product", productCode).param("merchant", merchantId).param("currency", currency).param("amount", amount)
        .query((rs, rowNum) -> Map.<String, Object>of("ruleId", rs.getString("rule_id"), "feeRate", rs.getBigDecimal("fee_rate"),
            "fixedFee", rs.getBigDecimal("fixed_fee"), "mode", rs.getString("fee_mode"), "scale", 2))
        .optional().orElseThrow(() -> unavailable("没有匹配的费率规则"));

    var risk = mybatisClient.sql("SELECT policy_id, decision FROM risk_policy WHERE release_version = :version AND status = 'ACTIVE' AND (JSON_UNQUOTE(JSON_EXTRACT(condition_json, '$.productCode')) IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(condition_json, '$.productCode')) = :product) AND (JSON_UNQUOTE(JSON_EXTRACT(condition_json, '$.currency')) IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(condition_json, '$.currency')) = :currency) ORDER BY priority LIMIT 1")
        .param("version", version).param("product", productCode).param("currency", currency)
        .query((rs, rowNum) -> Map.<String, Object>of("policyId", rs.getString("policy_id"), "decision", rs.getString("decision")))
        .optional().orElse(Map.of("decision", "PASS"));

    var result = new LinkedHashMap<String, Object>();
    result.put("merchantId", merchantId);
    result.put("productCode", productCode);
    result.put("paymentMethod", paymentMethod);
    result.put("country", country);
    result.put("currency", currency);
    result.put("amount", amount);
    result.put("configVersion", version);
    result.put("product", product);
    result.put("route", candidates.getFirst());
    result.put("pricing", pricing);
    result.put("risk", risk);
    result.put("candidates", candidates.stream().map(item -> item.get("channelId")).toList());
    return result;
  }

  public List<String> validate(long version) {
    var errors = new java.util.ArrayList<String>();
    if (count("SELECT COUNT(*) FROM routing_rule WHERE release_version = :version AND status = 'ACTIVE'", version) == 0) errors.add("至少需要一条路由规则");
    if (count("SELECT COUNT(*) FROM pricing_rule WHERE release_version = :version AND status = 'ACTIVE'", version) == 0) errors.add("至少需要一条费率规则");
    if (count("SELECT COUNT(*) FROM risk_policy WHERE release_version = :version AND status = 'ACTIVE'", version) == 0) errors.add("至少需要一条风控策略");
    if (count("SELECT COUNT(*) FROM routing_rule r LEFT JOIN channel c ON c.channel_id = r.channel_id AND c.status = 'ACTIVE' WHERE r.release_version = :version AND c.id IS NULL", version) > 0) errors.add("路由包含不存在或已停用的渠道");
    if (count("SELECT COUNT(*) FROM pricing_rule WHERE release_version = :version AND (fee_rate < 0 OR fixed_fee < 0 OR min_amount > max_amount)", version) > 0) errors.add("费率或金额区间不合法");
    if (count("SELECT COUNT(*) FROM routing_rule a JOIN routing_rule b ON a.id < b.id AND a.release_version = b.release_version AND a.product_code = b.product_code AND COALESCE(a.merchant_id, '') = COALESCE(b.merchant_id, '') AND a.payment_method = b.payment_method AND COALESCE(a.country, '') = COALESCE(b.country, '') AND a.currency = b.currency AND a.priority = b.priority WHERE a.release_version = :version AND a.status = 'ACTIVE' AND b.status = 'ACTIVE'", version) > 0) errors.add("路由规则存在相同作用域和优先级冲突");
    return errors;
  }

  private long count(String sql, long version) {
    return mybatisClient.sql(sql).param("version", version).query(Long.class).single();
  }

  private void requireExists(String sql, String value, String message) {
    if (mybatisClient.sql(sql).param("value", value).query(Long.class).single() == 0) throw unavailable(message);
  }

  private void requireBinding(String merchantId, String productCode) {
    var count = mybatisClient.sql("SELECT COUNT(*) FROM merchant_product WHERE merchant_id = :merchant AND product_code = :product AND status = 'ACTIVE'")
        .param("merchant", merchantId).param("product", productCode).query(Long.class).single();
    if (count == 0) throw unavailable("商户未开通当前产品");
  }

  private ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
  }
}
