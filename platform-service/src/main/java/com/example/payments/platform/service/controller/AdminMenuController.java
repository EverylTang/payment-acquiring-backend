package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/menus")
@RequiredArgsConstructor
public class AdminMenuController {
  private final AdminMenuService service;

  @GetMapping
  @PreAuthorize("hasAuthority('system:menu:list')")
  public AdminPageResponse<AdminMenuService.MenuResponse> list(
      @RequestParam(name = "page", defaultValue = "1") int page,
      @RequestParam(name = "pageSize", defaultValue = "100") int pageSize) {
    return service.list(page, pageSize);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('system:menu:create')")
  public AdminMenuService.MenuResponse create(
      @Valid @RequestBody AdminMenuService.CreateRequest request, Authentication authentication) {
    return service.create(request, authentication.getName());
  }

  @GetMapping("/resource-types")
  @PreAuthorize("hasAuthority('system:menu:resource-type:list')")
  public java.util.List<AdminMenuService.ResourceTypeResponse> resourceTypes() {
    return service.resourceTypes();
  }

  @PostMapping("/resource-types")
  @PreAuthorize("hasAuthority('system:menu:resource-type:create')")
  public AdminMenuService.ResourceTypeResponse createResourceType(
      @Valid @RequestBody AdminMenuService.ResourceTypeRequest request,
      Authentication authentication) {
    return service.createResourceType(request, authentication.getName());
  }

  @GetMapping("/{menuCode}/resource-types")
  @PreAuthorize("hasAuthority('system:menu:resource-type:list')")
  public java.util.List<String> menuResourceTypes(@PathVariable("menuCode") String menuCode) {
    return service.menuResourceTypes(menuCode);
  }

  @PatchMapping("/{menuCode}/status")
  @PreAuthorize("hasAuthority('system:menu:status')")
  public AdminMenuService.MenuResponse updateStatus(
      @PathVariable("menuCode") String menuCode,
      @Valid @RequestBody AdminMenuService.StatusRequest request,
      Authentication authentication) {
    return service.updateStatus(menuCode, request, authentication.getName());
  }

  @PutMapping("/{menuCode}")
  @PreAuthorize("hasAuthority('system:menu:update')")
  public AdminMenuService.MenuResponse update(
      @PathVariable("menuCode") String menuCode,
      @Valid @RequestBody AdminMenuService.UpdateRequest request,
      Authentication authentication) {
    return service.update(menuCode, request, authentication.getName());
  }

  @DeleteMapping("/{menuCode}")
  @PreAuthorize("hasAuthority('system:menu:delete')")
  public void delete(@PathVariable("menuCode") String menuCode, Authentication authentication) {
    service.delete(menuCode, authentication.getName());
  }

  @GetMapping("/{menuCode}/permissions")
  @PreAuthorize("hasAuthority('system:menu:permission:list')")
  public java.util.List<AdminMenuService.PermissionResponse> permissions(
      @PathVariable("menuCode") String menuCode) {
    return service.permissions(menuCode);
  }

  @PostMapping("/{menuCode}/permissions")
  @PreAuthorize("hasAuthority('system:menu:permission:create')")
  public AdminMenuService.PermissionResponse createPermission(
      @PathVariable("menuCode") String menuCode,
      @Valid @RequestBody AdminMenuService.PermissionRequest request,
      Authentication authentication) {
    return service.createPermission(menuCode, request, authentication.getName());
  }

  @PutMapping("/{menuCode}/permissions/{actionCode}")
  @PreAuthorize("hasAuthority('system:menu:permission:update')")
  public AdminMenuService.PermissionResponse updatePermission(
      @PathVariable("menuCode") String menuCode,
      @PathVariable("actionCode") String actionCode,
      @Valid @RequestBody AdminMenuService.PermissionUpdateRequest request,
      Authentication authentication) {
    return service.updatePermission(menuCode, actionCode, request, authentication.getName());
  }

  @DeleteMapping("/{menuCode}/permissions/{actionCode}")
  @PreAuthorize("hasAuthority('system:menu:permission:delete')")
  public void deletePermission(
      @PathVariable("menuCode") String menuCode,
      @PathVariable("actionCode") String actionCode,
      Authentication authentication) {
    service.deletePermission(menuCode, actionCode, authentication.getName());
  }
}
