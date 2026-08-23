package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.service.AdminMerchantAccessService;
import com.example.payments.platform.service.service.PlatformDataService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantProductAdminService {
  private final PlatformDataService mybatisClient;
  private final AdminMerchantAccessService accessService;

  public AdminPageResponse<MerchantProductResponse> list(
      int page, int pageSize, Authentication authentication) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var offset = (currentPage - 1) * size;
    var where = accessService.predicate(authentication, "mp");
    var total =
        accessService
            .bindScope(
                mybatisClient.sql("SELECT COUNT(*) FROM merchant_product mp WHERE " + where),
                authentication)
            .query(Long.class)
            .single();
    var items =
        accessService
            .bindScope(
                mybatisClient
                    .sql(
                        "SELECT mp.binding_id, mp.merchant_id, m.name merchant_name,"
                            + " mp.product_code, p.name product_name, mp.status, mp.created_at,"
                            + " mp.updated_at FROM merchant_product mp JOIN merchant m ON"
                            + " m.merchant_id = mp.merchant_id JOIN logical_product p ON"
                            + " p.product_code = mp.product_code WHERE "
                            + where
                            + " ORDER BY mp.created_at DESC LIMIT :limit OFFSET :offset")
                    .param("limit", size)
                    .param("offset", offset),
                authentication)
            .query(MerchantProductResponse.class)
            .list();
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  private List<MerchantProductResponse> all() {
    return mybatisClient
        .sql(
            "SELECT mp.binding_id, mp.merchant_id, m.name merchant_name, mp.product_code, p.name"
                + " product_name, mp.status, mp.created_at, mp.updated_at FROM merchant_product mp"
                + " JOIN merchant m ON m.merchant_id = mp.merchant_id JOIN logical_product p ON"
                + " p.product_code = mp.product_code ORDER BY mp.created_at DESC")
        .query(MerchantProductResponse.class)
        .list();
  }

  public MerchantProductResponse detail(String bindingId, Authentication authentication) {
    var where = accessService.predicate(authentication, "mp");
    return accessService
        .bindScope(
            mybatisClient
                .sql(
                    "SELECT mp.binding_id, mp.merchant_id, m.name merchant_name, mp.product_code,"
                        + " p.name product_name, mp.status, mp.created_at, mp.updated_at FROM"
                        + " merchant_product mp JOIN merchant m ON m.merchant_id = mp.merchant_id"
                        + " JOIN logical_product p ON p.product_code = mp.product_code WHERE"
                        + " mp.binding_id = :bindingId AND "
                        + where)
                .param("bindingId", bindingId),
            authentication)
        .query(MerchantProductResponse.class)
        .optional()
        .orElseThrow(() -> new IllegalArgumentException("商户产品绑定不存在: " + bindingId));
  }

  @Transactional
  public MerchantProductResponse bind(BindRequest request, Authentication authentication) {
    accessService.assertAllowed(authentication, request.merchantId());
    ensureActive("merchant", "merchant_id", request.merchantId());
    ensureActive("logical_product", "product_code", request.productCode());
    var duplicate =
        mybatisClient
            .sql(
                "SELECT COUNT(*) FROM merchant_product WHERE merchant_id = :merchantId AND"
                    + " product_code = :productCode")
            .param("merchantId", request.merchantId())
            .param("productCode", request.productCode())
            .query(Long.class)
            .single();
    if (duplicate > 0) throw new IllegalArgumentException("商户已绑定该产品");
    var now = Instant.now();
    var bindingId = UUID.randomUUID().toString();
    mybatisClient
        .sql(
            "INSERT INTO merchant_product (binding_id, merchant_id, product_code, status,"
                + " created_at, updated_at) VALUES (:bindingId, :merchantId, :productCode,"
                + " 'ACTIVE', :now, :now)")
        .param("bindingId", bindingId)
        .param("merchantId", request.merchantId())
        .param("productCode", request.productCode())
        .param("now", now)
        .update();
    audit(authentication.getName(), "BIND", bindingId);
    return detail(bindingId, authentication);
  }

  @Transactional
  public MerchantProductResponse update(
      String bindingId, UpdateRequest request, Authentication authentication) {
    accessService.assertAllowed(authentication, request.merchantId());
    detail(bindingId, authentication);
    ensureActive("merchant", "merchant_id", request.merchantId());
    ensureActive("logical_product", "product_code", request.productCode());
    var duplicate =
        mybatisClient
            .sql(
                "SELECT COUNT(*) FROM merchant_product WHERE merchant_id = :merchantId AND"
                    + " product_code = :productCode AND binding_id <> :bindingId")
            .param("merchantId", request.merchantId())
            .param("productCode", request.productCode())
            .param("bindingId", bindingId)
            .query(Long.class)
            .single();
    if (duplicate > 0) throw new IllegalArgumentException("商户已绑定该产品");
    var changed =
        mybatisClient
            .sql(
                "UPDATE merchant_product SET merchant_id = :merchantId, product_code ="
                    + " :productCode, updated_at = :now WHERE binding_id = :bindingId")
            .param("merchantId", request.merchantId())
            .param("productCode", request.productCode())
            .param("now", Instant.now())
            .param("bindingId", bindingId)
            .update();
    if (changed == 0) throw new IllegalArgumentException("商户产品绑定不存在: " + bindingId);
    audit(authentication.getName(), "UPDATE", bindingId);
    return detail(bindingId, authentication);
  }

  @Transactional
  public void changeStatus(String bindingId, StatusRequest request, Authentication authentication) {
    var current = detail(bindingId, authentication);
    accessService.assertAllowed(authentication, current.merchantId());
    mybatisClient
        .sql(
            "UPDATE merchant_product SET status = :status, updated_at = :now WHERE binding_id ="
                + " :bindingId")
        .param("status", request.status())
        .param("now", Instant.now())
        .param("bindingId", bindingId)
        .update();
    audit(authentication.getName(), "CHANGE_STATUS", bindingId);
  }

  private void ensureActive(String table, String idColumn, String value) {
    var active =
        mybatisClient
            .sql(
                "SELECT COUNT(*) FROM "
                    + table
                    + " WHERE "
                    + idColumn
                    + " = :value AND status = 'ACTIVE'")
            .param("value", value)
            .query(Long.class)
            .single();
    if (active == 0) throw new IllegalArgumentException("对象不存在或已停用: " + value);
  }

  private void audit(String operator, String action, String resourceId) {
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, created_at) VALUES (:audit, :operator, :action,"
                + " 'MERCHANT_PRODUCT', :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", operator)
        .param("action", action)
        .param("resourceId", resourceId)
        .param("now", Instant.now())
        .update();
  }

  public record BindRequest(@NotBlank String merchantId, @NotBlank String productCode) {}

  public record UpdateRequest(@NotBlank String merchantId, @NotBlank String productCode) {}

  public record StatusRequest(@NotBlank String status) {}

  public record MerchantProductResponse(
      String bindingId,
      String merchantId,
      String merchantName,
      String productCode,
      String productName,
      String status,
      Instant createdAt,
      Instant updatedAt) {}
}
