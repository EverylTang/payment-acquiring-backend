package com.example.payments.platform.service.controller;

import java.util.List;
import com.example.payments.platform.service.service.PlatformDataService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/access")
public class AdminAccessController {
  private final PlatformDataService mybatisClient;

  public AdminAccessController(PlatformDataService mybatisClient) {
    this.mybatisClient = mybatisClient;
  }

  @GetMapping
  public AccessResponse current(Authentication authentication) {
    var username = authentication.getName();
    var roles = mybatisClient.sql("SELECT r.role_code FROM admin_role r JOIN admin_user_role ur ON ur.role_id = r.id JOIN admin_user u ON u.id = ur.user_id WHERE u.username = :username AND u.status = 'ACTIVE' ORDER BY r.role_code")
        .param("username", username).query(String.class).list();
    var menus = mybatisClient.sql("SELECT DISTINCT m.menu_code, m.menu_name, m.menu_type, m.route_path, m.component_key, m.icon, m.sort_order FROM admin_menu m JOIN admin_role_menu rm ON rm.menu_id = m.id JOIN admin_role r ON r.id = rm.role_id JOIN admin_user_role ur ON ur.role_id = r.id JOIN admin_user u ON u.id = ur.user_id WHERE u.username = :username AND u.status = 'ACTIVE' AND m.status = 'ACTIVE' AND m.visible = TRUE ORDER BY m.sort_order, m.id")
        .param("username", username).query(MenuItem.class).list();
    var permissions = mybatisClient.sql("SELECT DISTINCT p.permission_code FROM admin_permission p JOIN admin_role_permission rp ON rp.permission_id = p.id JOIN admin_role r ON r.id = rp.role_id JOIN admin_user_role ur ON ur.role_id = r.id JOIN admin_user u ON u.id = ur.user_id WHERE u.username = :username AND u.status = 'ACTIVE' AND p.status = 'ACTIVE' ORDER BY p.permission_code")
        .param("username", username).query(String.class).list();
    return new AccessResponse(roles, menus, permissions);
  }

  public record AccessResponse(List<String> roles, List<MenuItem> menus, List<String> permissions) {}
  public record MenuItem(String menuCode, String menuName, String menuType, String routePath, String componentKey, String icon, int sortOrder) {}
}
