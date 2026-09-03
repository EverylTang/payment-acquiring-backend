package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminRoleMapper;
import com.example.payments.platform.service.model.RoleModel;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRoleService {
  private final AdminRoleMapper mapper;
  private final OperationAuditService audit;

  public Page list(int page, int size) {
    int p = Math.max(page, 1), s = Math.min(Math.max(size, 1), 100);
    return new Page(
        mapper.page(s, (p - 1) * s).stream()
            .map(r -> new RoleModel(r.id(), r.roleCode(), r.roleName()))
            .toList(),
        p,
        s,
        mapper.count());
  }

  public Permissions permissions(String code) {
    long id = roleId(code);
    return new Permissions(mapper.menus(id), mapper.permissions(id));
  }

  @Transactional
  public RoleModel updateName(String code, String name, String operator, Object payload) {
    long id = roleId(code);
    mapper.updateName(code, name);
    audit.record(operator, "UPDATE_ROLE", "ADMIN_ROLE", code, payload);
    return new RoleModel(id, code, name);
  }

  @Transactional
  public RoleModel create(
      String code,
      String name,
      List<String> menus,
      List<String> permissions,
      List<String> scopes,
      String operator,
      Object payload) {
    if (mapper.id(code) != null) throw new IllegalStateException("角色编码已存在: " + code);
    validate(menus, mapper.validMenus(menus), "菜单");
    validate(permissions, mapper.validPermissions(permissions), "权限");
    validateScopes(scopes);
    mapper.insertRole(code, name);
    long id = roleId(code);
    menus.forEach(menu -> mapper.addMenu(id, menu));
    permissions.forEach(permission -> mapper.addPermission(id, permission));
    scopes.forEach(scope -> mapper.addScope(id, scope));
    audit.record(operator, "CREATE_ROLE", "ADMIN_ROLE", code, payload);
    return new RoleModel(id, code, name);
  }

  @Transactional
  public Permissions update(
      String code, List<String> menus, List<String> permissions, String operator, Object payload) {
    long id = roleId(code);
    if ("ADMIN".equals(code)) permissions = mapper.activePermissions();
    validate(menus, mapper.validMenus(menus), "菜单");
    validate(permissions, mapper.validPermissions(permissions), "权限");
    mapper.clearMenus(id);
    menus.forEach(m -> mapper.addMenu(id, m));
    mapper.clearPermissions(id);
    permissions.forEach(p -> mapper.addPermission(id, p));
    audit.record(operator, "UPDATE_PERMISSION", "ADMIN_ROLE", code, payload);
    return permissions(code);
  }

  private long roleId(String code) {
    var id = mapper.id(code);
    if (id == null) throw new IllegalArgumentException("角色不存在: " + code);
    return id;
  }

  private void validate(List<String> values, long valid, String label) {
    if (values == null
        || values.stream().anyMatch(v -> v == null || v.isBlank())
        || values.size() != new HashSet<>(values).size()
        || valid != values.size()) throw new IllegalArgumentException("存在无效或重复" + label + "编码");
  }

  private void validateScopes(List<String> scopes) {
    if (scopes == null
        || scopes.isEmpty()
        || scopes.size() != new HashSet<>(scopes).size()
        || scopes.stream().anyMatch(scope -> !List.of("ALL", "ASSIGNED", "SELF").contains(scope))
        || (scopes.contains("ALL") && scopes.size() > 1))
      throw new IllegalArgumentException("数据范围配置无效");
  }

  public record Page(List<RoleModel> items, int page, int pageSize, long total) {}

  public record Permissions(List<String> menuCodes, List<String> permissionCodes) {}
}
