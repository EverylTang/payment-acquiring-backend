package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminPermissionCatalogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPermissionCatalogService {
  private final AdminPermissionCatalogMapper mapper;

  public Catalog catalog() {
    return new Catalog(
        mapper.menus().stream()
            .map(
                v ->
                    new Menu(
                        v.id(),
                        v.menuCode(),
                        v.menuName(),
                        v.parentId(),
                        v.menuType(),
                        v.status(),
                        v.visible(),
                        v.sortOrder()))
            .toList(),
        mapper.permissions().stream()
            .map(
                v ->
                    new Permission(
                        v.permissionCode(), v.permissionName(), v.resourceType(), v.status()))
            .toList());
  }

  public record Catalog(List<Menu> menus, List<Permission> permissions) {}

  public record Menu(
      long id,
      String menuCode,
      String menuName,
      long parentId,
      String menuType,
      String status,
      boolean visible,
      int sortOrder) {}

  public record Permission(
      String permissionCode, String permissionName, String resourceType, String status) {}
}
