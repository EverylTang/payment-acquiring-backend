package com.example.payments.platform.service.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/merchant-products")
public class AdminMerchantProductController {
  private final JdbcClient jdbcClient;

  public AdminMerchantProductController(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @GetMapping
  public List<MerchantProductResponse> list() {
    return jdbcClient.sql("SELECT mp.binding_id, mp.merchant_id, m.name merchant_name, mp.product_code, p.name product_name, mp.status, mp.created_at, mp.updated_at FROM merchant_product mp JOIN merchant m ON m.merchant_id = mp.merchant_id JOIN logical_product p ON p.product_code = mp.product_code ORDER BY mp.created_at DESC")
        .query(MerchantProductResponse.class).list();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public MerchantProductResponse bind(@Valid @RequestBody BindRequest request, Authentication authentication) {
    ensureActive("merchant", "merchant_id", request.merchantId());
    ensureActive("logical_product", "product_code", request.productCode());
    var now = Timestamp.from(Instant.now());
    var bindingId = UUID.randomUUID().toString();
    jdbcClient.sql("INSERT INTO merchant_product (binding_id, merchant_id, product_code, status, created_at, updated_at) VALUES (:bindingId, :merchantId, :productCode, 'ACTIVE', :now, :now)")
        .param("bindingId", bindingId).param("merchantId", request.merchantId()).param("productCode", request.productCode()).param("now", now).update();
    audit(authentication.getName(), "BIND", bindingId);
    return list().stream().filter(item -> item.bindingId().equals(bindingId)).findFirst().orElseThrow();
  }

  @PatchMapping("/{bindingId}/status")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public void changeStatus(@PathVariable String bindingId, @Valid @RequestBody StatusRequest request, Authentication authentication) {
    jdbcClient.sql("UPDATE merchant_product SET status = :status, updated_at = :now WHERE binding_id = :bindingId")
        .param("status", request.status()).param("now", Timestamp.from(Instant.now())).param("bindingId", bindingId).update();
    audit(authentication.getName(), "CHANGE_STATUS", bindingId);
  }

  private void ensureActive(String table, String idColumn, String value) {
    var active = jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = :value AND status = 'ACTIVE'")
        .param("value", value).query(Long.class).single();
    if (active == 0) throw new IllegalArgumentException("对象不存在或已停用: " + value);
  }

  private void audit(String operator, String action, String resourceId) {
    jdbcClient.sql("INSERT INTO operation_audit (audit_id, operator_id, action, resource_type, resource_id, created_at) VALUES (:audit, :operator, :action, 'MERCHANT_PRODUCT', :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString()).param("operator", operator).param("action", action).param("resourceId", resourceId).param("now", Timestamp.from(Instant.now())).update();
  }

  public record BindRequest(@NotBlank String merchantId, @NotBlank String productCode) {}
  public record StatusRequest(@NotBlank String status) {}
  public record MerchantProductResponse(String bindingId, String merchantId, String merchantName, String productCode, String productName, String status, Instant createdAt, Instant updatedAt) {}
}
