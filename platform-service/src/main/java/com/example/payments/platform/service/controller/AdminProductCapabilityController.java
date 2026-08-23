package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.model.ProductCapabilityModel;
import com.example.payments.platform.service.service.ProductCapabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/products/{productCode}/capabilities")
@RequiredArgsConstructor
public class AdminProductCapabilityController {
  private final ProductCapabilityService capabilityService;

  @GetMapping
  public AdminPageResponse<CapabilityResponse> list(
      @PathVariable String productCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    var r = capabilityService.list(productCode, page, pageSize);
    return new AdminPageResponse<>(
        r.items().stream().map(AdminProductCapabilityController::response).toList(),
        r.page(),
        r.pageSize(),
        r.total());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('product-capability:list')")
  public CapabilityResponse create(
      @PathVariable String productCode,
      @Valid @RequestBody CapabilityRequest request,
      Authentication authentication) {
    return response(
        capabilityService.create(productCode, command(request), authentication.getName(), request));
  }

  @PutMapping("/{capabilityId}")
  @PreAuthorize("hasAuthority('product-capability:create')")
  public CapabilityResponse update(
      @PathVariable String productCode,
      @PathVariable String capabilityId,
      @Valid @RequestBody CapabilityRequest request,
      Authentication authentication) {
    return response(
        capabilityService.update(
            productCode, capabilityId, command(request), authentication.getName(), request));
  }

  @PatchMapping("/{capabilityId}/status")
  @PreAuthorize("hasAuthority('product-capability:update')")
  public CapabilityResponse status(
      @PathVariable String productCode,
      @PathVariable String capabilityId,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    return response(
        capabilityService.changeStatus(
            productCode, capabilityId, request.status(), authentication.getName(), request));
  }

  private static ProductCapabilityService.Command command(CapabilityRequest r) {
    return new ProductCapabilityService.Command(
        r.country(),
        r.currency(),
        r.paymentMethod(),
        r.minAmount(),
        r.maxAmount(),
        r.supportsRefund());
  }

  private static CapabilityResponse response(ProductCapabilityModel v) {
    return new CapabilityResponse(
        v.capabilityId(),
        v.productCode(),
        v.country(),
        v.currency(),
        v.paymentMethod(),
        v.minAmount(),
        v.maxAmount(),
        v.supportsRefund(),
        v.status());
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
