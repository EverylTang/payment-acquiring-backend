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
      @RequestParam(name = "page", defaultValue = "1") int page,
      @RequestParam(name = "pageSize", defaultValue = "100") int pageSize) {
    return service.list(page, pageSize);
  }

  @PostMapping
  public AdminMenuService.MenuResponse create(
      @Valid @RequestBody AdminMenuService.CreateRequest request, Authentication authentication) {
    return service.create(request, authentication.getName());
  }

  @GetMapping("/resource-types")
  public java.util.List<AdminMenuService.ResourceTypeResponse> resourceTypes() {
    return service.resourceTypes();
  }

  @PostMapping("/resource-types")
  public AdminMenuService.ResourceTypeResponse createResourceType(
      @Valid @RequestBody AdminMenuService.ResourceTypeRequest request,
      Authentication authentication) {
    return service.createResourceType(request, authentication.getName());
  }

  @GetMapping("/{menuCode}/resource-types")
  public java.util.List<String> menuResourceTypes(@PathVariable("menuCode") String menuCode) {
    return service.menuResourceTypes(menuCode);
  }

  @PatchMapping("/{menuCode}/status")
  public AdminMenuService.MenuResponse updateStatus(
      @PathVariable("menuCode") String menuCode,
      @Valid @RequestBody AdminMenuService.StatusRequest request,
      Authentication authentication) {
    return service.updateStatus(menuCode, request, authentication.getName());
  }

  @PutMapping("/{menuCode}")
  public AdminMenuService.MenuResponse update(
      @PathVariable("menuCode") String menuCode,
      @Valid @RequestBody AdminMenuService.UpdateRequest request,
      Authentication authentication) {
    return service.update(menuCode, request, authentication.getName());
  }

  @DeleteMapping("/{menuCode}")
  public void delete(@PathVariable("menuCode") String menuCode, Authentication authentication) {
    service.delete(menuCode, authentication.getName());
  }

  @GetMapping("/{menuCode}/permissions")
  public java.util.List<AdminMenuService.PermissionResponse> permissions(
      @PathVariable("menuCode") String menuCode) {
    return service.permissions(menuCode);
  }

  @PostMapping("/{menuCode}/permissions")
  public AdminMenuService.PermissionResponse createPermission(
      @PathVariable("menuCode") String menuCode,
      @Valid @RequestBody AdminMenuService.PermissionRequest request,
      Authentication authentication) {
    return service.createPermission(menuCode, request, authentication.getName());
  }

  @PutMapping("/{menuCode}/permissions/{actionCode}")
  public AdminMenuService.PermissionResponse updatePermission(
      @PathVariable("menuCode") String menuCode,
      @PathVariable("actionCode") String actionCode,
      @Valid @RequestBody AdminMenuService.PermissionUpdateRequest request,
      Authentication authentication) {
    return service.updatePermission(menuCode, actionCode, request, authentication.getName());
  }

  @DeleteMapping("/{menuCode}/permissions/{actionCode}")
  public void deletePermission(
      @PathVariable("menuCode") String menuCode,
      @PathVariable("actionCode") String actionCode,
      Authentication authentication) {
    service.deletePermission(menuCode, actionCode, authentication.getName());
  }
}
