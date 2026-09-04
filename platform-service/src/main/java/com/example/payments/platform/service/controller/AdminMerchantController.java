package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.model.MerchantModel;
import com.example.payments.platform.service.service.MerchantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequiredArgsConstructor
public class AdminMerchantController {
  private final MerchantService merchantService;

  @GetMapping
  @PreAuthorize("hasAuthority('merchant:list')")
  public AdminPageResponse<MerchantResponse> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String merchantName,
      @RequestParam(required = false) String merchantId,
      @RequestParam(required = false) @Pattern(regexp = "ACTIVE|DISABLED") String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdTo,
      Authentication authentication) {
    var result =
        merchantService.list(
            page,
            pageSize,
            new MerchantService.MerchantFilter(
                merchantName, merchantId, status, createdFrom, createdTo),
            authentication);
    return new AdminPageResponse<>(
        result.items().stream().map(AdminMerchantController::response).toList(),
        result.page(),
        result.pageSize(),
        result.total());
  }

  @GetMapping("/{merchantId}")
  @PreAuthorize("hasAuthority('merchant:detail')")
  public MerchantResponse detail(@PathVariable String merchantId, Authentication authentication) {
    return response(merchantService.detail(merchantId, authentication));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('merchant:create')")
  public MerchantResponse create(
      @Valid @RequestBody CreateRequest request, Authentication authentication) {
    return response(merchantService.create(request.merchantId(), request.name(), authentication));
  }

  @PutMapping("/{merchantId}")
  @PreAuthorize("hasAuthority('merchant:update')")
  public MerchantResponse update(
      @PathVariable String merchantId,
      @Valid @RequestBody UpdateRequest request,
      Authentication authentication) {
    return response(merchantService.update(merchantId, request.name(), authentication));
  }

  @PatchMapping("/{merchantId}/status")
  @PreAuthorize("hasAuthority('merchant:status')")
  public MerchantResponse status(
      @PathVariable String merchantId,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    return response(merchantService.changeStatus(merchantId, request.status(), authentication));
  }

  private static MerchantResponse response(MerchantModel value) {
    return new MerchantResponse(
        value.merchantId(), value.name(), value.status(), value.createdAt(), value.updatedAt());
  }

  public record CreateRequest(String merchantId, @NotBlank String name) {}

  public record UpdateRequest(@NotBlank String name) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record MerchantResponse(
      String merchantId, String name, String status, Instant createdAt, Instant updatedAt) {}
}
