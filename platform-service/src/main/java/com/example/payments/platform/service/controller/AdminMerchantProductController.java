package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.admin.MerchantProductAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/merchant-products")
@RequiredArgsConstructor
public class AdminMerchantProductController {
  private final MerchantProductAdminService service;

  @GetMapping
  public AdminPageResponse<MerchantProductAdminService.MerchantProductResponse> list(
      @RequestParam(defaultValue = "1") int p,
      @RequestParam(defaultValue = "20") int s,
      Authentication a) {
    return service.list(p, s, a);
  }

  @GetMapping("/{id}")
  public MerchantProductAdminService.MerchantProductResponse detail(
      @PathVariable("id") String id, Authentication a) {
    return service.detail(id, a);
  }

  @PostMapping
  public MerchantProductAdminService.MerchantProductResponse bind(
      @RequestBody MerchantProductAdminService.BindRequest r, Authentication a) {
    return service.bind(r, a);
  }

  @PutMapping("/{id}")
  public MerchantProductAdminService.MerchantProductResponse update(
      @PathVariable("id") String id,
      @RequestBody MerchantProductAdminService.UpdateRequest r,
      Authentication a) {
    return service.update(id, r, a);
  }

  @PatchMapping("/{id}/status")
  public void changeStatus(
      @PathVariable("id") String id,
      @RequestBody MerchantProductAdminService.StatusRequest r,
      Authentication a) {
    service.changeStatus(id, r, a);
  }
}
