package com.example.payments.platform.service.interfaces.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/products/{productCode}/capabilities")
public class AdminProductCapabilityController {
  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public AdminProductCapabilityController(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public AdminPageResponse<CapabilityResponse> list(
      @PathVariable String productCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var total =
        jdbcClient
            .sql("SELECT COUNT(*) FROM product_capability WHERE product_code = :productCode")
            .param("productCode", productCode)
            .query(Long.class)
            .single();
    var items =
        jdbcClient
            .sql(
                "SELECT capability_id, product_code, country, currency, payment_method, min_amount,"
                    + " max_amount, supports_refund, status FROM product_capability WHERE"
                    + " product_code = :productCode ORDER BY id LIMIT :limit OFFSET :offset")
            .param("productCode", productCode)
            .param("limit", size)
            .param("offset", (currentPage - 1) * size)
            .query(CapabilityResponse.class)
            .list();
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('product-capability:list')")
  @Transactional
  public CapabilityResponse create(
      @PathVariable String productCode,
      @Valid @RequestBody CapabilityRequest request,
      Authentication authentication) {
    ensureProduct(productCode);
    ensureRange(request);
    var capabilityId = UUID.randomUUID().toString();
    jdbcClient
        .sql(
            "INSERT INTO product_capability (capability_id, product_code, country, currency,"
                + " payment_method, min_amount, max_amount, supports_refund, status) VALUES (:id,"
                + " :product, :country, :currency, :method, :min, :max, :refund, 'ACTIVE')")
        .param("id", capabilityId)
        .param("product", productCode)
        .param("country", request.country())
        .param("currency", request.currency())
        .param("method", request.paymentMethod())
        .param("min", request.minAmount())
        .param("max", request.maxAmount())
        .param("refund", request.supportsRefund())
        .update();
    audit(authentication.getName(), "CREATE", capabilityId, request);
    return detail(capabilityId);
  }

  @PutMapping("/{capabilityId}")
  @PreAuthorize("hasAuthority('product-capability:create')")
  @Transactional
  public CapabilityResponse update(
      @PathVariable String productCode,
      @PathVariable String capabilityId,
      @Valid @RequestBody CapabilityRequest request,
      Authentication authentication) {
    ensureRange(request);
    jdbcClient
        .sql(
            "UPDATE product_capability SET country = :country, currency = :currency, payment_method"
                + " = :method, min_amount = :min, max_amount = :max, supports_refund = :refund"
                + " WHERE capability_id = :id AND product_code = :product")
        .param("country", request.country())
        .param("currency", request.currency())
        .param("method", request.paymentMethod())
        .param("min", request.minAmount())
        .param("max", request.maxAmount())
        .param("refund", request.supportsRefund())
        .param("id", capabilityId)
        .param("product", productCode)
        .update();
    audit(authentication.getName(), "UPDATE", capabilityId, request);
    return detail(capabilityId);
  }

  @PatchMapping("/{capabilityId}/status")
  @PreAuthorize("hasAuthority('product-capability:update')")
  @Transactional
  public CapabilityResponse status(
      @PathVariable String productCode,
      @PathVariable String capabilityId,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    jdbcClient
        .sql(
            "UPDATE product_capability SET status = :status WHERE capability_id = :id AND"
                + " product_code = :product")
        .param("status", request.status())
        .param("id", capabilityId)
        .param("product", productCode)
        .update();
    audit(authentication.getName(), "CHANGE_STATUS", capabilityId, request);
    return detail(capabilityId);
  }

  private CapabilityResponse detail(String capabilityId) {
    return jdbcClient
        .sql(
            "SELECT capability_id, product_code, country, currency, payment_method, min_amount,"
                + " max_amount, supports_refund, status FROM product_capability WHERE capability_id"
                + " = :id")
        .param("id", capabilityId)
        .query(CapabilityResponse.class)
        .single();
  }

  private void ensureProduct(String productCode) {
    if (jdbcClient
            .sql("SELECT COUNT(*) FROM logical_product WHERE product_code = :code")
            .param("code", productCode)
            .query(Long.class)
            .single()
        == 0) throw new IllegalArgumentException("产品不存在: " + productCode);
  }

  private void ensureRange(CapabilityRequest request) {
    if (request.maxAmount().compareTo(request.minAmount()) < 0)
      throw new IllegalArgumentException("最大金额不能小于最小金额");
  }

  private void audit(String operator, String action, String id, Object payload) {
    jdbcClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, after_summary, created_at) VALUES (:audit, :operator, :action,"
                + " 'PRODUCT_CAPABILITY', :id, :summary, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", operator)
        .param("action", action)
        .param("id", id)
        .param("summary", json(payload))
        .param("now", Timestamp.from(Instant.now()))
        .update();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("审计摘要无法序列化", exception);
    }
  }

  public record CapabilityRequest(
      @NotBlank String country,
      @Pattern(regexp = "[A-Z]{3}") String currency,
      @NotBlank String paymentMethod,
      @DecimalMin("0.01") BigDecimal minAmount,
      @DecimalMin("0.01") BigDecimal maxAmount,
      boolean supportsRefund) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record CapabilityResponse(
      String capabilityId,
      String productCode,
      String country,
      String currency,
      String paymentMethod,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      boolean supportsRefund,
      String status) {}
}
