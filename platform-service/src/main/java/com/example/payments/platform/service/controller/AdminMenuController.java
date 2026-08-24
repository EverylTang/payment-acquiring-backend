package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/menus")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMenuController {
  private final AdminMenuService service;

  @GetMapping
  public AdminPageResponse<AdminMenuService.MenuResponse> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "100") int pageSize) {
    return service.list(page, pageSize);
  }

  @PostMapping
  public AdminMenuService.MenuResponse create(
      @Valid @RequestBody AdminMenuService.CreateRequest request, Authentication authentication) {
    return service.create(request, authentication.getName());
  }

  @PatchMapping("/{menuCode}/status")
  public AdminMenuService.MenuResponse updateStatus(
      @PathVariable String menuCode,
      @Valid @RequestBody AdminMenuService.StatusRequest request,
      Authentication authentication) {
    return service.updateStatus(menuCode, request, authentication.getName());
  }
}
