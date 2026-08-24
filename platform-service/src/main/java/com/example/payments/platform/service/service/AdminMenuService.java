package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.MybatisPlusClient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMenuService {
  private final MybatisPlusClient mybatisClient;
  private final OperationAuditService auditService;

  public AdminPageResponse<MenuResponse> list(int page, int pageSize) {
    int currentPage = Math.max(page, 1);
    int size = Math.min(Math.max(pageSize, 1), 100);
    long total = mybatisClient.sql("SELECT COUNT(*) FROM admin_menu").query(Long.class).single();
    var items =
        mybatisClient
            .sql(
                "SELECT id, parent_id, menu_code, menu_name, menu_type, route_path, component_key,"
                    + " icon, sort_order, visible, status FROM admin_menu ORDER BY parent_id,"
                    + " sort_order, id LIMIT :limit OFFSET :offset")
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
      boolean visible) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

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
}
