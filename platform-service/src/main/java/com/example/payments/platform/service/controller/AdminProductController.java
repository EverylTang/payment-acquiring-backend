package com.example.payments.platform.service.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;
import com.example.payments.platform.service.service.PlatformDataService;
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
@RequestMapping("/api/admin/v1/products")
public class AdminProductController {
  private final PlatformDataService mybatisClient;

  private final ObjectMapper objectMapper;

  public AdminProductController(PlatformDataService mybatisClient, ObjectMapper objectMapper) {
    this.mybatisClient = mybatisClient;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('product:list')")
  public AdminPageResponse<ProductResponse> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var offset = (currentPage - 1) * size;
    var total = mybatisClient.sql("SELECT COUNT(*) FROM logical_product").query(Long.class).single();
    var items =
        mybatisClient
            .sql(
                "SELECT product_code, name, status, created_at, updated_at FROM logical_product"
                    + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
            .param("limit", size)
            .param("offset", offset)
            .query(ProductResponse.class)
            .list();
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  @GetMapping("/{productCode}")
  @PreAuthorize("hasAuthority('product:detail')")
  public ProductResponse detail(@PathVariable String productCode) {
    return mybatisClient
        .sql(
            "SELECT product_code, name, status, created_at, updated_at FROM logical_product WHERE"
                + " product_code = :productCode")
        .param("productCode", productCode)
        .query(ProductResponse.class)
        .single();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('product:create')")
  @Transactional
  public ProductResponse create(
      @Valid @RequestBody CreateRequest request, Authentication authentication) {
    var now = Instant.now();
    mybatisClient
        .sql(
            "INSERT INTO logical_product (product_code, name, status, created_at, updated_at)"
                + " VALUES (:code, :name, 'ACTIVE', :now, :now)")
        .param("code", request.productCode())
        .param("name", request.name())
        .param("now", now)
        .update();
    audit(authentication.getName(), "CREATE", request.productCode(), request);
    return detail(request.productCode());
  }

  @PutMapping("/{productCode}")
  @PreAuthorize("hasAuthority('product:update')")
  @Transactional
  public ProductResponse update(
      @PathVariable String productCode,
      @Valid @RequestBody UpdateRequest request,
      Authentication authentication) {
    mybatisClient
        .sql(
            "UPDATE logical_product SET name = :name, updated_at = :now WHERE product_code ="
                + " :productCode")
        .param("name", request.name())
        .param("now", Instant.now())
        .param("productCode", productCode)
        .update();
    audit(authentication.getName(), "UPDATE", productCode, request);
    return detail(productCode);
  }

  @PatchMapping("/{productCode}/status")
  @PreAuthorize("hasAuthority('product:status')")
  @Transactional
  public ProductResponse status(
      @PathVariable String productCode,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    mybatisClient
        .sql(
            "UPDATE logical_product SET status = :status, updated_at = :now WHERE product_code ="
                + " :productCode")
        .param("status", request.status())
        .param("now", Instant.now())
        .param("productCode", productCode)
        .update();
    audit(authentication.getName(), "CHANGE_STATUS", productCode, request);
    return detail(productCode);
  }

  private void audit(String operator, String action, String id, Object payload) {
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, after_summary, created_at) VALUES (:audit, :operator, :action,"
                + " 'PRODUCT', :id, :summary, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", operator)
        .param("action", action)
        .param("id", id)
        .param("summary", json(payload))
        .param("now", Instant.now())
        .update();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("审计摘要无法序列化", exception);
    }
  }

  public record CreateRequest(@NotBlank String productCode, @NotBlank String name) {}

  public record UpdateRequest(@NotBlank String name) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record ProductResponse(
      String productCode, String name, String status, Instant createdAt, Instant updatedAt) {}
}
