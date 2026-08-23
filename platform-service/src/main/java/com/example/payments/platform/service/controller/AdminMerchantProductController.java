package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.MerchantProductAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/merchant-products")
@RequiredArgsConstructor
public class AdminMerchantProductController {
  private final MerchantProductAdminService service;

  @GetMapping
  @PreAuthorize("hasAuthority('merchant-product:list')")
  public AdminPageResponse<MerchantProductAdminService.MerchantProductResponse> list(
      @RequestParam(defaultValue = "1") int p,
      @RequestParam(defaultValue = "20") int s,
      Authentication a) {
    return service.list(p, s, a);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('merchant-product:detail')")
  public MerchantProductAdminService.MerchantProductResponse detail(
      @PathVariable("id") String id, Authentication a) {
    return service.detail(id, a);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('merchant-product:bind')")
  public MerchantProductAdminService.MerchantProductResponse bind(
      @Valid @RequestBody MerchantProductAdminService.BindRequest r, Authentication a) {
    return service.bind(r, a);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('merchant-product:update')")
  public MerchantProductAdminService.MerchantProductResponse update(
      @PathVariable("id") String id,
      @Valid @RequestBody MerchantProductAdminService.UpdateRequest r,
      Authentication a) {
    return service.update(id, r, a);
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAuthority('merchant-product:status')")
  public void changeStatus(
      @PathVariable("id") String id,
      @Valid @RequestBody MerchantProductAdminService.StatusRequest r,
      Authentication a) {
    service.changeStatus(id, r, a);
  }
}
