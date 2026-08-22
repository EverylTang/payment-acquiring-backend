package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminMerchantAccessService;
import com.example.payments.platform.service.service.PlatformDataService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
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
@RequestMapping("/api/admin/v1/merchants")
public class AdminMerchantController {
  private final PlatformDataService mybatisClient;
  private final AdminMerchantAccessService accessService;
  private final ObjectMapper objectMapper;

  public AdminMerchantController(
      PlatformDataService mybatisClient,
      AdminMerchantAccessService accessService,
      ObjectMapper objectMapper) {
    this.mybatisClient = mybatisClient;
    this.accessService = accessService;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('merchant:list')")
  public AdminPageResponse<MerchantResponse> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      Authentication authentication) {
    var paging = new Paging(page, pageSize);
    var where = accessService.predicate(authentication, "m");
    var total =
        accessService
            .bindScope(
                mybatisClient.sql("SELECT COUNT(*) FROM merchant m WHERE " + where), authentication)
            .query(Long.class)
            .single();
    var items =
        accessService
            .bindScope(
                mybatisClient
                    .sql(
                        "SELECT merchant_id, name, status, settlement_currency, created_at,"
                            + " updated_at FROM merchant m WHERE "
                            + where
                            + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
                    .param("limit", paging.size())
                    .param("offset", paging.offset()),
                authentication)
            .query(MerchantResponse.class)
            .list();
    return new AdminPageResponse<>(items, paging.page(), paging.size(), total);
  }

  @GetMapping("/{merchantId}")
  @PreAuthorize("hasAuthority('merchant:detail')")
  public MerchantResponse detail(@PathVariable String merchantId, Authentication authentication) {
    var where = accessService.predicate(authentication, "m");
    return accessService
        .bindScope(
            mybatisClient
                .sql(
                    "SELECT merchant_id, name, status, settlement_currency, created_at, updated_at"
                        + " FROM merchant m WHERE merchant_id = :merchantId AND "
                        + where)
                .param("merchantId", merchantId),
            authentication)
        .query(MerchantResponse.class)
        .single();
  }

  private record Paging(int page, int size) {
    Paging {
      page = Math.max(page, 1);
      size = Math.min(Math.max(size, 1), 100);
    }

    int offset() {
      return (page - 1) * size;
    }
  }

  @PostMapping
  @PreAuthorize("hasAuthority('merchant:create')")
  @Transactional
  public MerchantResponse create(
      @Valid @RequestBody CreateRequest request, Authentication authentication) {
    var now = Instant.now();
    mybatisClient
        .sql(
            "INSERT INTO merchant (merchant_id, name, status, settlement_currency, created_at,"
                + " updated_at) VALUES (:id, :name, 'ACTIVE', :currency, :now, :now)")
        .param("id", request.merchantId())
        .param("name", request.name())
        .param("currency", request.settlementCurrency())
        .param("now", now)
        .update();
    audit(authentication.getName(), "CREATE", request.merchantId(), request);
    return detail(request.merchantId(), authentication);
  }

  @PutMapping("/{merchantId}")
  @PreAuthorize("hasAuthority('merchant:update')")
  @Transactional
  public MerchantResponse update(
      @PathVariable String merchantId,
      @Valid @RequestBody UpdateRequest request,
      Authentication authentication) {
    mybatisClient
        .sql(
            "UPDATE merchant SET name = :name, settlement_currency = :currency, updated_at = :now"
                + " WHERE merchant_id = :merchantId")
        .param("name", request.name())
        .param("currency", request.settlementCurrency())
        .param("now", Instant.now())
        .param("merchantId", merchantId)
        .update();
    audit(authentication.getName(), "UPDATE", merchantId, request);
    return detail(merchantId, authentication);
  }

  @PatchMapping("/{merchantId}/status")
  @PreAuthorize("hasAuthority('merchant:status')")
  @Transactional
  public MerchantResponse status(
      @PathVariable String merchantId,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    mybatisClient
        .sql(
            "UPDATE merchant SET status = :status, updated_at = :now WHERE merchant_id ="
                + " :merchantId")
        .param("status", request.status())
        .param("now", Instant.now())
        .param("merchantId", merchantId)
        .update();
    audit(authentication.getName(), "CHANGE_STATUS", merchantId, request);
    return detail(merchantId, authentication);
  }

  private void audit(String operator, String action, String id, Object payload) {
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, after_summary, created_at) VALUES (:audit, :operator, :action,"
                + " 'MERCHANT', :id, :summary, :now)")
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

  public record CreateRequest(
      @NotBlank String merchantId,
      @NotBlank String name,
      @Pattern(regexp = "[A-Z]{3}") String settlementCurrency) {}

  public record UpdateRequest(
      @NotBlank String name, @Pattern(regexp = "[A-Z]{3}") String settlementCurrency) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record MerchantResponse(
      String merchantId,
      String name,
      String status,
      String settlementCurrency,
      Instant createdAt,
      Instant updatedAt) {}
}
