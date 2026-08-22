package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.PlatformDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {
  private final PlatformDataService mybatisClient;

  public AdminRoleController(PlatformDataService mybatisClient) {
    this.mybatisClient = mybatisClient;
  }

  @GetMapping
  public AdminPageResponse<RoleResponse> list( @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var total = mybatisClient.sql("SELECT COUNT(*) FROM admin_role").query(Long.class).single();
    var items =
        mybatisClient
            .sql(
                "SELECT id, role_code, role_name FROM admin_role ORDER BY role_code LIMIT :limit"
                    + " OFFSET :offset")
            .param("limit", size)
            .param("offset", (currentPage - 1) * size)
            .query(RoleResponse.class)
            .list();
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  @GetMapping("/{roleCode}/permissions")
  public RolePermissions permissions(@PathVariable String roleCode) {
    var roleId = roleId(roleCode);
    var permissionCodes =
        mybatisClient
            .sql(
                "SELECT p.permission_code FROM admin_permission p JOIN admin_role_permission rp ON"
                    + " rp.permission_id = p.id WHERE rp.role_id = :roleId ORDER BY"
                    + " p.permission_code")
            .param("roleId", roleId)
            .query(String.class)
            .list();
    var menuCodes =
        mybatisClient
            .sql(
                "SELECT m.menu_code FROM admin_menu m JOIN admin_role_menu rm ON rm.menu_id = m.id"
                    + " WHERE rm.role_id = :roleId ORDER BY m.sort_order, m.id")
            .param("roleId", roleId)
            .query(String.class)
            .list();
    return new RolePermissions(menuCodes, permissionCodes);
  }

  @PutMapping("/{roleCode}/permissions")
  @Transactional
  public RolePermissions updatePermissions(
      @PathVariable String roleCode,
      @Valid @RequestBody PermissionUpdateRequest request,
      Authentication authentication) {
    var roleId = roleId(roleCode);
    validateCodes(request.menuCodes(), "menu_code", "admin_menu", "菜单");
    validateCodes(request.permissionCodes(), "permission_code", "admin_permission", "权限");
    mybatisClient
        .sql("DELETE FROM admin_role_menu WHERE role_id = :roleId")
        .param("roleId", roleId)
        .update();
    mybatisClient
        .sql("DELETE FROM admin_role_permission WHERE role_id = :roleId")
        .param("roleId", roleId)
        .update();
    request
        .menuCodes()
        .forEach(
            code ->
                mybatisClient
                    .sql(
                        "INSERT INTO admin_role_menu (role_id, menu_id) SELECT :roleId, id FROM"
                            + " admin_menu WHERE menu_code = :code")
                    .param("roleId", roleId)
                    .param("code", code)
                    .update());
    request
        .permissionCodes()
        .forEach(
            code ->
                mybatisClient
                    .sql(
                        "INSERT INTO admin_role_permission (role_id, permission_id) SELECT :roleId,"
                            + " id FROM admin_permission WHERE permission_code = :code")
                    .param("roleId", roleId)
                    .param("code", code)
                    .update());
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, created_at) VALUES (:audit, :operator, 'UPDATE_PERMISSION',"
                + " 'ADMIN_ROLE', :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", authentication.getName())
        .param("resourceId", roleCode)
        .param("now", Instant.now())
        .update();
    return permissions(roleCode);
  }

  private long roleId(String roleCode) {
    return mybatisClient
        .sql("SELECT id FROM admin_role WHERE role_code = :roleCode")
        .param("roleCode", roleCode)
        .query(Long.class)
        .single();
  }

  private void validateCodes(List<String> codes, String column, String table, String label) {
    if (codes.stream().anyMatch(code -> code == null || code.isBlank())
        || codes.size() != new HashSet<>(codes).size()) {
      throw new IllegalArgumentException(label + "编码不能为空且不能重复");
    }
    var activeCount =
        mybatisClient
            .sql(
                "SELECT COUNT(*) FROM "
                    + table
                    + " WHERE "
                    + column
                    + " IN (:codes) AND status = 'ACTIVE'")
            .param("codes", codes)
            .query(Long.class)
            .single();
    if (activeCount != codes.size()) throw new IllegalArgumentException("存在无效的" + label + "编码");
  }

  public record RoleResponse(long id, String roleCode, String roleName) {}

  public record RolePermissions(List<String> menuCodes, List<String> permissionCodes) {}

  public record PermissionUpdateRequest( @NotNull List<String> menuCodes, @NotNull List<String> permissionCodes) {}
}
