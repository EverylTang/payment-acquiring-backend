package com.example.payments.platform.service.interfaces.rest;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/permission-catalog")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionCatalogController {
  private final JdbcClient jdbcClient;

  public AdminPermissionCatalogController(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @GetMapping
  public PermissionCatalog catalog() {
    var menus =
        jdbcClient
            .sql(
                "SELECT menu_code, menu_name, parent_id, menu_type, status, visible, sort_order"
                    + " FROM admin_menu WHERE status = 'ACTIVE' ORDER BY parent_id, sort_order, id")
            .query(MenuResponse.class)
            .list();
    var permissions =
        jdbcClient
            .sql(
                "SELECT permission_code, permission_name, resource_type, status FROM"
                    + " admin_permission WHERE status = 'ACTIVE' ORDER BY resource_type,"
                    + " permission_code")
            .query(PermissionResponse.class)
            .list();
    return new PermissionCatalog(menus, permissions);
  }

  public record PermissionCatalog(List<MenuResponse> menus, List<PermissionResponse> permissions) {}

  public record MenuResponse(
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
