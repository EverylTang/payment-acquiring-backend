package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.AdminMenuMapper;
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
  private final AdminMenuMapper mapper;
  private final OperationAuditService auditService;

  public AdminPageResponse<MenuResponse> list(int page, int pageSize, MenuFilter filter) {
    int currentPage = Math.max(page, 1);
    int size = Math.min(Math.max(pageSize, 1), 100);
    long total = mapper.count(filter.menuName(), filter.menuCode(), filter.menuType(), filter.status());
    var items = mapper.selectPage(filter.menuName(), filter.menuCode(), filter.menuType(), filter.status(), size, (currentPage - 1) * size);
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  @Transactional
  public MenuResponse create(CreateRequest request, String operator) {
    long parentId = parentId(request.parentMenuCode());
    var now = Instant.now();
    mapper.insertMenu(parentId, request.menuCode(), request.menuName(), request.menuType(), blankToNull(request.routePath()), blankToNull(request.componentKey()), blankToNull(request.icon()), request.sortOrder(), request.visible(), now);
    replaceResourceTypes(find(request.menuCode()).id(), request.resourceTypes());
    auditService.record(operator, "CREATE", "ADMIN_MENU", request.menuCode(), request);
    return find(request.menuCode());
  }

  @Transactional
  public MenuResponse updateStatus(String menuCode, StatusRequest request, String operator) {
    mapper.updateStatus(request.status(), Instant.now(), menuCode);
    auditService.record(operator, "CHANGE_STATUS", "ADMIN_MENU", menuCode, request);
    return find(menuCode);
  }

  @Transactional
  public MenuResponse update(String menuCode, UpdateRequest request, String operator) {
    var current = find(menuCode);
    long parentId = parentId(request.parentMenuCode());
    if (parentId == current.id()) throw new IllegalArgumentException("父级菜单不能是自身");
    mapper.updateMenu(parentId, request.menuName(), request.menuType(), blankToNull(request.routePath()), blankToNull(request.componentKey()), blankToNull(request.icon()), request.sortOrder(), request.visible(), Instant.now(), menuCode);
    replaceResourceTypes(current.id(), request.resourceTypes());
    auditService.record(operator, "UPDATE", "ADMIN_MENU", menuCode, request);
    return find(menuCode);
  }

  @Transactional
  public void delete(String menuCode, String operator) {
    var current = find(menuCode);
    long children = mapper.countChildren(current.id());
    if (children > 0) throw new IllegalStateException("请先删除或迁移子菜单");
    var prefix = menuCode + ":%";
    mapper.deleteRolePermissionsByPrefix(prefix);
    mapper.deletePermissionsByPrefix(prefix);
    mapper.deleteRoleMenus(current.id());
    mapper.deleteMenuResourceTypes(current.id());
    mapper.deleteMenu(current.id());
    auditService.record(operator, "DELETE", "ADMIN_MENU", menuCode, null);
  }

  public List<PermissionResponse> permissions(String menuCode) {
    find(menuCode);
    return mapper.selectPermissions(menuCode + ":%");
  }

  public List<ResourceTypeResponse> resourceTypes() {
    return mapper.selectActiveResourceTypes();
  }

  @Transactional
  public ResourceTypeResponse createResourceType(ResourceTypeRequest request, String operator) {
    mapper.insertResourceType(request.resourceType(), request.resourceName());
    auditService.record(operator, "CREATE", "ADMIN_RESOURCE_TYPE", request.resourceType(), request);
    return new ResourceTypeResponse(request.resourceType(), request.resourceName());
  }

  public List<String> menuResourceTypes(String menuCode) {
    var menu = find(menuCode);
    return mapper.selectMenuResourceTypes(menu.id());
  }

  @Transactional
  public PermissionResponse createPermission(
      String menuCode, PermissionRequest request, String operator) {
    find(menuCode);
    validateResourceType(menuCode, request.resourceType());
    var code = permissionCode(menuCode, request.actionCode());
    mapper.insertPermission(code, request.permissionName(), request.resourceType(), Instant.now());
    auditService.record(operator, "CREATE", "ADMIN_PERMISSION", code, request);
    return permission(code);
  }

  @Transactional
  public PermissionResponse updatePermission(
      String menuCode, String actionCode, PermissionUpdateRequest request, String operator) {
    find(menuCode);
    validateResourceType(menuCode, request.resourceType());
    var code = permissionCode(menuCode, actionCode);
    mapper.updatePermission(request.permissionName(), request.resourceType(), request.status(), Instant.now(), code);
    auditService.record(operator, "UPDATE", "ADMIN_PERMISSION", code, request);
    return permission(code);
  }

  @Transactional
  public void deletePermission(String menuCode, String actionCode, String operator) {
    find(menuCode);
    var code = permissionCode(menuCode, actionCode);
    mapper.deleteRolePermission(code);
    mapper.deletePermission(code);
    auditService.record(operator, "DELETE", "ADMIN_PERMISSION", code, null);
  }

  private long parentId(String parentMenuCode) {
    if (parentMenuCode == null || parentMenuCode.isBlank()) return 0;
    var id = mapper.selectMenuId(parentMenuCode);
    if (id == null) throw new IllegalArgumentException("父级菜单不存在: " + parentMenuCode);
    return id;
  }

  private MenuResponse find(String menuCode) {
    var menu = mapper.selectMenu(menuCode);
    if (menu == null) throw new IllegalArgumentException("菜单不存在: " + menuCode);
    return menu;
  }

  private PermissionResponse permission(String code) {
    var permission = mapper.selectPermission(code);
    if (permission == null) throw new IllegalArgumentException("操作权限不存在: " + code);
    return permission;
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
    mapper.deleteMenuResourceTypes(menuId);
    types.forEach(type -> mapper.insertMenuResourceType(menuId, type));
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
