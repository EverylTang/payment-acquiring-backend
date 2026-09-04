package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.MerchantProductMapper;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MerchantProductAdminService {
  private final MerchantProductMapper mapper;
  private final AdminMerchantAccessService accessService;
  private final OperationAuditService auditService;

  public AdminPageResponse<MerchantProductResponse> list(
      int page, int pageSize, MerchantProductFilter filter, Authentication authentication) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var offset = (currentPage - 1) * size;
    var hasAllScope = accessService.hasAllScope(authentication.getName());
    var total = countPage(filter, hasAllScope, authentication.getName());
    var items =
        mapper.selectPage(
            filter.merchantName(),
            filter.merchantId(),
            filter.productName(),
            filter.productCode(),
            filter.status(),
            hasAllScope,
            authentication.getName(),
            size,
            offset);
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  public MerchantProductResponse detail(String bindingId, Authentication authentication) {
    return java.util.Optional.ofNullable(
            mapper.selectByBindingId(
                bindingId,
                accessService.hasAllScope(authentication.getName()),
                authentication.getName()))
        .orElseThrow(() -> new IllegalArgumentException("商户产品绑定不存在: " + bindingId));
  }

  @Transactional
  public MerchantProductResponse bind(BindRequest request, Authentication authentication) {
    accessService.assertAllowed(authentication, request.merchantId());
    ensureActiveMerchant(request.merchantId());
    ensureActiveProduct(request.productCode());
    var duplicate = mapper.countByMerchantAndProduct(request.merchantId(), request.productCode());
    if (duplicate > 0) throw duplicateBinding();
    var now = Instant.now();
    var bindingId = UUID.randomUUID().toString();
    mapper.insert(bindingId, request.merchantId(), request.productCode(), now);
    audit(authentication.getName(), "BIND", bindingId);
    return detail(bindingId, authentication);
  }

  @Transactional
  public MerchantProductResponse update(
      String bindingId, UpdateRequest request, Authentication authentication) {
    accessService.assertAllowed(authentication, request.merchantId());
    detail(bindingId, authentication);
    ensureActiveMerchant(request.merchantId());
    ensureActiveProduct(request.productCode());
    var duplicate =
        mapper.countOtherByMerchantAndProduct(
            request.merchantId(), request.productCode(), bindingId);
    if (duplicate > 0) throw duplicateBinding();
    var changed =
        mapper.update(bindingId, request.merchantId(), request.productCode(), Instant.now());
    if (changed == 0) throw new IllegalArgumentException("商户产品绑定不存在: " + bindingId);
    audit(authentication.getName(), "UPDATE", bindingId);
    return detail(bindingId, authentication);
  }

  @Transactional
  public void changeStatus(String bindingId, StatusRequest request, Authentication authentication) {
    var current = detail(bindingId, authentication);
    accessService.assertAllowed(authentication, current.merchantId());
    mapper.updateStatus(bindingId, request.status(), Instant.now());
    audit(authentication.getName(), "CHANGE_STATUS", bindingId);
  }

  private void ensureActiveMerchant(String merchantId) {
    var active = mapper.countActiveMerchant(merchantId);
    if (active == 0) throw new IllegalArgumentException("对象不存在或已停用: " + merchantId);
  }

  private void ensureActiveProduct(String productCode) {
    var active = mapper.countActiveProduct(productCode);
    if (active == 0) throw new IllegalArgumentException("对象不存在或已停用: " + productCode);
  }

  private long countPage(MerchantProductFilter filter, boolean hasAllScope, String username) {
    return mapper.countPage(
        filter.merchantName(),
        filter.merchantId(),
        filter.productName(),
        filter.productCode(),
        filter.status(),
        hasAllScope,
        username);
  }

  private static ResponseStatusException duplicateBinding() {
    return new ResponseStatusException(HttpStatus.CONFLICT, "商户已绑定该产品");
  }

  private void audit(String operator, String action, String resourceId) {
    auditService.recordAction(operator, action, "MERCHANT_PRODUCT", resourceId);
  }

  public record BindRequest(@NotBlank String merchantId, @NotBlank String productCode) {}

  public record UpdateRequest(@NotBlank String merchantId, @NotBlank String productCode) {}

  public record StatusRequest(@NotBlank String status) {}

  public record MerchantProductFilter(
      String merchantName,
      String merchantId,
      String productName,
      String productCode,
      String status) {
    public MerchantProductFilter {
      merchantName = normalize(merchantName);
      merchantId = normalize(merchantId);
      productName = normalize(productName);
      productCode = normalize(productCode);
      status = normalize(status);
    }

    private static String normalize(String value) {
      return value == null || value.isBlank() ? null : value.trim();
    }
  }

  public record MerchantProductResponse(
      String bindingId,
      String merchantId,
      String merchantName,
      String productCode,
      String productName,
      String status,
      Instant createdAt,
      Instant updatedAt,
      String supportedPaymentMethods) {}
}
