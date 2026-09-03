package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.model.ProductModel;
import com.example.payments.platform.service.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/products")
@RequiredArgsConstructor
public class AdminProductController {
  private final ProductService productService;

  @GetMapping
  @PreAuthorize("hasAuthority('product:list')")
  public AdminPageResponse<ProductResponse> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String productType) {
    var result = productService.list(page, pageSize, status, productType);
    return new AdminPageResponse<>(
        result.items().stream().map(AdminProductController::response).toList(),
        result.page(),
        result.pageSize(),
        result.total());
  }

  @GetMapping("/{productCode}")
  @PreAuthorize("hasAuthority('product:detail')")
  public ProductResponse detail(@PathVariable String productCode) {
    return response(productService.detail(productCode));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('product:create')")
  public ProductResponse create(
      @Valid @RequestBody CreateRequest request, Authentication authentication) {
    return response(
        productService.create(
            request.productCode(), command(request), authentication.getName(), request));
  }

  @PutMapping("/{productCode}")
  @PreAuthorize("hasAuthority('product:update')")
  public ProductResponse update(
      @PathVariable String productCode,
      @Valid @RequestBody UpdateRequest request,
      Authentication authentication) {
    return response(
        productService.update(productCode, command(request), authentication.getName(), request));
  }

  @PatchMapping("/{productCode}/status")
  @PreAuthorize("hasAuthority('product:status')")
  public ProductResponse status(
      @PathVariable String productCode,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    return response(
        productService.changeStatus(
            productCode, request.status(), authentication.getName(), request));
  }

  private static ProductService.Command command(CreateRequest v) {
    return new ProductService.Command(
        v.name(),
        v.productType(),
        v.accessMode(),
        v.defaultCountry(),
        v.defaultCurrency(),
        v.description(),
        v.statementDescriptor());
  }

  private static ProductService.Command command(UpdateRequest v) {
    return new ProductService.Command(
        v.name(),
        v.productType(),
        v.accessMode(),
        v.defaultCountry(),
        v.defaultCurrency(),
        v.description(),
        v.statementDescriptor());
  }

  private static ProductResponse response(ProductModel v) {
    return new ProductResponse(
        v.productCode(),
        v.name(),
        v.productType(),
        v.accessMode(),
        v.defaultCountry(),
        v.defaultCurrency(),
        v.description(),
        v.statementDescriptor(),
        v.status(),
        v.activeCapabilityCount(),
        v.supportedPaymentMethods(),
        v.createdAt(),
        v.updatedAt());
  }

  public record CreateRequest(
      @NotBlank String productCode,
      @NotBlank String name,
      @NotBlank @Pattern(regexp = "PAYIN|PAYOUT") String productType,
      @Pattern(regexp = "DIRECT|AGGREGATED") String accessMode,
      @NotBlank @Pattern(regexp = "[A-Z]{2}") String defaultCountry,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String defaultCurrency,
      @Size(max = 1000) String description,
      @Size(max = 22) String statementDescriptor) {}

  public record UpdateRequest(
      @NotBlank String name,
      @NotBlank @Pattern(regexp = "PAYIN|PAYOUT") String productType,
      @Pattern(regexp = "DIRECT|AGGREGATED") String accessMode,
      @NotBlank @Pattern(regexp = "[A-Z]{2}") String defaultCountry,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String defaultCurrency,
      @Size(max = 1000) String description,
      @Size(max = 22) String statementDescriptor) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record ProductResponse(
      String productCode,
      String name,
      String productType,
      String accessMode,
      String defaultCountry,
      String defaultCurrency,
      String description,
      String statementDescriptor,
      String status,
      long activeCapabilityCount,
      String supportedPaymentMethods,
      Instant createdAt,
      Instant updatedAt) {}
}
