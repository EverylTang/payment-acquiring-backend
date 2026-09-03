package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.MybatisPlusClient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMenuService {
  private final MybatisPlusClient mybatisClient;
  private final OperationAuditService auditService;

  public AdminPageResponse<MenuResponse> list(int page, int pageSize, MenuFilter filter) {
    int currentPage = Math.max(page, 1);
    int size = Math.min(Math.max(pageSize, 1), 100);
    String conditions = " WHERE (:menuName IS NULL OR LOWER(menu_name) LIKE CONCAT('%', LOWER(:menuName), '%'))"
        + " AND (:menuCode IS NULL OR LOWER(menu_code) LIKE CONCAT('%', LOWER(:menuCode), '%'))"
        + " AND (:menuType IS NULL OR menu_type = :menuType)"
        + " AND (:status IS NULL OR status = :status)";
    long total = mybatisClient.sql("SELECT COUNT(*) FROM admin_menu" + conditions)
        .param("menuName", filter.menuName()).param("menuCode", filter.menuCode())
        .param("menuType", filter.menuType()).param("status", filter.status())
        .query(Long.class).single();
    var items =
        mybatisClient
            .sql(
                "SELECT id, parent_id, menu_code, menu_name, menu_type, route_path, component_key,"
                    + " icon, sort_order, visible, status FROM admin_menu" + conditions + " ORDER BY parent_id,"
                    + " sort_order, id LIMIT :limit OFFSET :offset")
            .param("menuName", filter.menuName())
            .param("menuCode", filter.menuCode())
            .param("menuType", filter.menuType())
            .param("status", filter.status())
            .param("limit", size)
            .param("offset", (currentPage - 1) * size)
            .query(MenuResponse.class)
            .list();
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  @Transactional
  public MenuResponse create(CreateRequest request, String operator) {
    long parentId = parentId(request.parentMenuCode());
    var now = Instant.now();
    mybatisClient
        .sql(
            "INSERT INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path,"
                + " component_key, icon, sort_order, visible, status, created_at, updated_at)"
                + " VALUES (:parentId, :code, :name, :type, :path, :component, :icon, :sort,"
                + " :visible, 'ACTIVE', :now, :now)")
        .param("parentId", parentId)
        .param("code", request.menuCode())
        .param("name", request.menuName())
        .param("type", request.menuType())
        .param("path", blankToNull(request.routePath()))
        .param("component", blankToNull(request.componentKey()))
        .param("icon", blankToNull(request.icon()))
        .param("sort", request.sortOrder())
        .param("visible", request.visible())
        .param("now", now)
        .update();
    replaceResourceTypes(find(request.menuCode()).id(), request.resourceTypes());
    auditService.record(operator, "CREATE", "ADMIN_MENU", request.menuCode(), request);
    return find(request.menuCode());
  }

  @Transactional
  public MenuResponse updateStatus(String menuCode, StatusRequest request, String operator) {
    mybatisClient
        .sql("UPDATE admin_menu SET status = :status, updated_at = :now WHERE menu_code = :code")
        .param("status", request.status())
        .param("now", Instant.now())
        .param("code", menuCode)
        .update();
    auditService.record(operator, "CHANGE_STATUS", "ADMIN_MENU", menuCode, request);
    return find(menuCode);
  }

  @Transactional
  public MenuResponse update(String menuCode, UpdateRequest request, String operator) {
    var current = find(menuCode);
    long parentId = parentId(request.parentMenuCode());
    if (parentId == current.id()) throw new IllegalArgumentException("父级菜单不能是自身");
    mybatisClient
        .sql(
            "UPDATE admin_menu SET parent_id=:parentId, menu_name=:name, menu_type=:type,"
                + " route_path=:path, component_key=:component, icon=:icon, sort_order=:sort,"
                + " visible=:visible, updated_at=:now WHERE menu_code=:code")
        .param("parentId", parentId)
        .param("name", request.menuName())
        .param("type", request.menuType())
        .param("path", blankToNull(request.routePath()))
        .param("component", blankToNull(request.componentKey()))
        .param("icon", blankToNull(request.icon()))
        .param("sort", request.sortOrder())
        .param("visible", request.visible())
        .param("now", Instant.now())
        .param("code", menuCode)
        .update();
    replaceResourceTypes(current.id(), request.resourceTypes());
    auditService.record(operator, "UPDATE", "ADMIN_MENU", menuCode, request);
    return find(menuCode);
  }

  @Transactional
  public void delete(String menuCode, String operator) {
    var current = find(menuCode);
    long children =
        mybatisClient
            .sql("SELECT COUNT(*) FROM admin_menu WHERE parent_id=:parentId")
            .param("parentId", current.id())
            .query(Long.class)
            .single();
    if (children > 0) throw new IllegalStateException("请先删除或迁移子菜单");
    var prefix = menuCode + ":%";
    mybatisClient
        .sql(
            "DELETE rp FROM admin_role_permission rp JOIN admin_permission p ON p.id=rp.permission_id"
                + " WHERE p.permission_code LIKE :prefix")
        .param("prefix", prefix)
        .update();
    mybatisClient
        .sql("DELETE FROM admin_permission WHERE permission_code LIKE :prefix")
        .param("prefix", prefix)
        .update();
    mybatisClient
        .sql("DELETE FROM admin_role_menu WHERE menu_id=:menuId")
        .param("menuId", current.id())
        .update();
    mybatisClient
        .sql("DELETE FROM admin_menu_resource_type WHERE menu_id=:menuId")
        .param("menuId", current.id())
        .update();
    mybatisClient
        .sql("DELETE FROM admin_menu WHERE id=:menuId")
        .param("menuId", current.id())
        .update();
    auditService.record(operator, "DELETE", "ADMIN_MENU", menuCode, null);
  }

  public List<PermissionResponse> permissions(String menuCode) {
    find(menuCode);
    return mybatisClient
        .sql(
            "SELECT permission_code, permission_name, resource_type, status FROM admin_permission"
                + " WHERE permission_code LIKE :prefix ORDER BY permission_code")
        .param("prefix", menuCode + ":%")
        .query(PermissionResponse.class)
        .list();
  }

  public List<ResourceTypeResponse> resourceTypes() {
    return mybatisClient
        .sql(
            "SELECT resource_type, resource_name FROM admin_resource_type WHERE status='ACTIVE' ORDER BY resource_type")
        .query(ResourceTypeResponse.class)
        .list();
  }

  @Transactional
  public ResourceTypeResponse createResourceType(ResourceTypeRequest request, String operator) {
    mybatisClient
        .sql(
            "INSERT INTO admin_resource_type(resource_type, resource_name, status)"
                + " VALUES(:type, :name, 'ACTIVE')")
        .param("type", request.resourceType())
        .param("name", request.resourceName())
        .update();
    auditService.record(operator, "CREATE", "ADMIN_RESOURCE_TYPE", request.resourceType(), request);
    return new ResourceTypeResponse(request.resourceType(), request.resourceName());
  }

  public List<String> menuResourceTypes(String menuCode) {
    var menu = find(menuCode);
    return mybatisClient
        .sql(
            "SELECT resource_type FROM admin_menu_resource_type WHERE menu_id=:menuId ORDER BY resource_type")
        .param("menuId", menu.id())
        .query(String.class)
        .list();
  }

  @Transactional
  public PermissionResponse createPermission(
      String menuCode, PermissionRequest request, String operator) {
    find(menuCode);
    validateResourceType(menuCode, request.resourceType());
    var code = permissionCode(menuCode, request.actionCode());
    mybatisClient
        .sql(
            "INSERT INTO admin_permission(permission_code, permission_name, resource_type, status,"
                + " created_at, updated_at) VALUES(:code, :name, :type, 'ACTIVE', :now, :now)")
        .param("code", code)
        .param("name", request.permissionName())
        .param("type", request.resourceType())
        .param("now", Instant.now())
        .update();
    auditService.record(operator, "CREATE", "ADMIN_PERMISSION", code, request);
    return permission(code);
  }

  @Transactional
  public PermissionResponse updatePermission(
      String menuCode, String actionCode, PermissionUpdateRequest request, String operator) {
    find(menuCode);
    validateResourceType(menuCode, request.resourceType());
    var code = permissionCode(menuCode, actionCode);
    mybatisClient
        .sql(
            "UPDATE admin_permission SET permission_name=:name, resource_type=:type, status=:status,"
                + " updated_at=:now WHERE permission_code=:code")
        .param("name", request.permissionName())
        .param("type", request.resourceType())
        .param("status", request.status())
        .param("now", Instant.now())
        .param("code", code)
        .update();
    auditService.record(operator, "UPDATE", "ADMIN_PERMISSION", code, request);
    return permission(code);
  }

  @Transactional
  public void deletePermission(String menuCode, String actionCode, String operator) {
    find(menuCode);
    var code = permissionCode(menuCode, actionCode);
    mybatisClient
        .sql(
            "DELETE rp FROM admin_role_permission rp JOIN admin_permission p ON p.id=rp.permission_id"
                + " WHERE p.permission_code=:code")
        .param("code", code)
        .update();
    mybatisClient
        .sql("DELETE FROM admin_permission WHERE permission_code=:code")
        .param("code", code)
        .update();
    auditService.record(operator, "DELETE", "ADMIN_PERMISSION", code, null);
  }

  private long parentId(String parentMenuCode) {
    if (parentMenuCode == null || parentMenuCode.isBlank()) return 0;
    return mybatisClient
        .sql("SELECT id FROM admin_menu WHERE menu_code = :code")
        .param("code", parentMenuCode)
        .query(Long.class)
        .optional()
        .orElseThrow(() -> new IllegalArgumentException("父级菜单不存在: " + parentMenuCode));
  }

  private MenuResponse find(String menuCode) {
    return mybatisClient
        .sql(
            "SELECT id, parent_id, menu_code, menu_name, menu_type, route_path, component_key,"
                + " icon, sort_order, visible, status FROM admin_menu WHERE menu_code = :code")
        .param("code", menuCode)
        .query(MenuResponse.class)
        .single();
  }

  private PermissionResponse permission(String code) {
    return mybatisClient
        .sql(
            "SELECT permission_code, permission_name, resource_type, status FROM admin_permission"
                + " WHERE permission_code=:code")
        .param("code", code)
        .query(PermissionResponse.class)
        .optional()
        .orElseThrow(() -> new IllegalArgumentException("操作权限不存在: " + code));
  }

  private String permissionCode(String menuCode, String actionCode) {
    return menuCode + ":" + actionCode;
  }

  private void validateResourceType(String menuCode, String resourceType) {
    if (!menuResourceTypes(menuCode).contains(resourceType))
      throw new IllegalArgumentException("资源类型未关联至菜单: " + resourceType);
  }

  private void replaceResourceTypes(long menuId, List<String> types) {
    if (types == null || types.isEmpty() || types.size() != new HashSet<>(types).size())
      throw new IllegalArgumentException("请至少选择一个有效资源类型");
    var valid = resourceTypes().stream().map(ResourceTypeResponse::resourceType).toList();
    if (!valid.containsAll(types)) throw new IllegalArgumentException("存在无效资源类型");
    mybatisClient
        .sql("DELETE FROM admin_menu_resource_type WHERE menu_id=:menuId")
        .param("menuId", menuId)
        .update();
    types.forEach(
        type ->
            mybatisClient
                .sql(
                    "INSERT INTO admin_menu_resource_type(menu_id, resource_type) VALUES(:menuId, :type)")
                .param("menuId", menuId)
                .param("type", type)
                .update());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record CreateRequest(
      @NotBlank String menuCode,
      @NotBlank String menuName,
      @Pattern(regexp = "DIRECTORY|PAGE") String menuType,
      String parentMenuCode,
      String routePath,
      String componentKey,
      String icon,
      int sortOrder,
      boolean visible,
      List<@NotBlank @Size(max = 64) String> resourceTypes) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record UpdateRequest(
      @NotBlank String menuName,
      @Pattern(regexp = "DIRECTORY|PAGE") String menuType,
      String parentMenuCode,
      String routePath,
      String componentKey,
      String icon,
      int sortOrder,
      boolean visible,
      List<@NotBlank @Size(max = 64) String> resourceTypes) {}

  public record PermissionRequest(
      @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{0,63}") String actionCode,
      @NotBlank String permissionName,
      @NotBlank @Size(max = 64) String resourceType) {}

  public record PermissionUpdateRequest(
      @NotBlank String permissionName,
      @NotBlank @Size(max = 64) String resourceType,
      @Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record MenuResponse(
      long id,
      long parentId,
      String menuCode,
      String menuName,
      String menuType,
      String routePath,
      String componentKey,
      String icon,
      int sortOrder,
      boolean visible,
      String status) {}

  public record MenuFilter(String menuName, String menuCode, String menuType, String status) {
    public MenuFilter {
      menuName = normalize(menuName);
      menuCode = normalize(menuCode);
      menuType = normalize(menuType);
      status = normalize(status);
    }

    private static String normalize(String value) {
      return value == null || value.isBlank() ? null : value.trim();
    }
  }

  public record PermissionResponse(
      String permissionCode, String permissionName, String resourceType, String status) {}

  public record ResourceTypeResponse(String resourceType, String resourceName) {}

  public record ResourceTypeRequest(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String resourceType,
      @NotBlank @Size(max = 128) String resourceName) {}
}
