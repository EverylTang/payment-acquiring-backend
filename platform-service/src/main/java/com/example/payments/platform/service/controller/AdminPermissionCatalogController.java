package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminPermissionCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/permission-catalog")
@RequiredArgsConstructor
public class AdminPermissionCatalogController {
  private final AdminPermissionCatalogService service;

  @GetMapping
  @PreAuthorize("hasAuthority('system:permission:list')")
  public PermissionCatalog catalog() {
    var c = service.catalog();
    return new PermissionCatalog(
        c.menus().stream()
            .map(
                v ->
                    new MenuResponse(
                        v.id(),
                        v.menuCode(),
                        v.menuName(),
                        v.parentId(),
                        v.menuType(),
                        v.status(),
                        v.visible(),
                        v.sortOrder()))
            .toList(),
        c.permissions().stream()
            .map(
                v ->
                    new PermissionResponse(
                        v.permissionCode(), v.permissionName(), v.resourceType(), v.status()))
            .toList());
  }

  public record PermissionCatalog(List<MenuResponse> menus, List<PermissionResponse> permissions) {}

  public record MenuResponse(
      long id,
      String menuCode,
      String menuName,
      long parentId,
      String menuType,
      String status,
      boolean visible,
      int sortOrder) {}

  public record PermissionResponse(
      String permissionCode, String permissionName, String resourceType, String status) {}
}
