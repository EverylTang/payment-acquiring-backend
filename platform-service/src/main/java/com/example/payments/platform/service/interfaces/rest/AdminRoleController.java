package com.example.payments.platform.service.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {
  private final JdbcClient jdbcClient;

  public AdminRoleController(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @GetMapping
  public List<RoleResponse> list() {
    return jdbcClient.sql("SELECT id, role_code, role_name FROM admin_role ORDER BY role_code")
        .query(RoleResponse.class).list();
  }

  @GetMapping("/{roleCode}/permissions")
  public RolePermissions permissions(@PathVariable String roleCode) {
    var roleId = roleId(roleCode);
    var permissionCodes = jdbcClient.sql("SELECT p.permission_code FROM admin_permission p JOIN admin_role_permission rp ON rp.permission_id = p.id WHERE rp.role_id = :roleId ORDER BY p.permission_code")
        .param("roleId", roleId).query(String.class).list();
    var menuCodes = jdbcClient.sql("SELECT m.menu_code FROM admin_menu m JOIN admin_role_menu rm ON rm.menu_id = m.id WHERE rm.role_id = :roleId ORDER BY m.sort_order, m.id")
        .param("roleId", roleId).query(String.class).list();
    return new RolePermissions(menuCodes, permissionCodes);
  }

  @PutMapping("/{roleCode}/permissions")
  @Transactional
  public RolePermissions updatePermissions(@PathVariable String roleCode, @Valid @RequestBody PermissionUpdateRequest request, Authentication authentication) {
    var roleId = roleId(roleCode);
    jdbcClient.sql("DELETE FROM admin_role_menu WHERE role_id = :roleId").param("roleId", roleId).update();
    jdbcClient.sql("DELETE FROM admin_role_permission WHERE role_id = :roleId").param("roleId", roleId).update();
    request.menuCodes().forEach(code -> jdbcClient.sql("INSERT INTO admin_role_menu (role_id, menu_id) SELECT :roleId, id FROM admin_menu WHERE menu_code = :code").param("roleId", roleId).param("code", code).update());
    request.permissionCodes().forEach(code -> jdbcClient.sql("INSERT INTO admin_role_permission (role_id, permission_id) SELECT :roleId, id FROM admin_permission WHERE permission_code = :code").param("roleId", roleId).param("code", code).update());
    jdbcClient.sql("INSERT INTO operation_audit (audit_id, operator_id, action, resource_type, resource_id, created_at) VALUES (:audit, :operator, 'UPDATE_PERMISSION', 'ADMIN_ROLE', :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString()).param("operator", authentication.getName()).param("resourceId", roleCode).param("now", Timestamp.from(Instant.now())).update();
    return permissions(roleCode);
  }

  private long roleId(String roleCode) {
    return jdbcClient.sql("SELECT id FROM admin_role WHERE role_code = :roleCode").param("roleCode", roleCode).query(Long.class).single();
  }

  public record RoleResponse(long id, String roleCode, String roleName) {}
  public record RolePermissions(List<String> menuCodes, List<String> permissionCodes) {}
  public record PermissionUpdateRequest(@NotNull List<String> menuCodes, @NotNull List<String> permissionCodes) {}
}
