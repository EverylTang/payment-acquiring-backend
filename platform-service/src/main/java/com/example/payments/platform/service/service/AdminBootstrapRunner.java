package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.MybatisPlusClient;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

  private final MybatisPlusClient mybatisClient;
  private final PasswordEncoder passwordEncoder;
  private final boolean enabled;
  private final String username;
  private final String password;
  private final String displayName;

  public AdminBootstrapRunner(
      MybatisPlusClient mybatisClient,
      PasswordEncoder passwordEncoder,
      @Value("${platform.security.admin-bootstrap.enabled:true}") boolean enabled,
      @Value("${platform.security.admin-bootstrap.username:}") String username,
      @Value("${platform.security.admin-bootstrap.password:}") String password,
      @Value("${platform.security.admin-bootstrap.display-name:系统管理员}") String displayName) {
    this.mybatisClient = mybatisClient;
    this.passwordEncoder = passwordEncoder;
    this.enabled = enabled;
    this.username = username;
    this.password = password;
    this.displayName = displayName;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    ensureSystemMenu();
    ensureAdminPermissions();
    if (hasAdminUser()) {
      log.info("管理员账号已存在，跳过初始化");
      return;
    }
    if (!enabled) {
      log.info("管理员 Bootstrap 已禁用，跳过初始化");
      return;
    }
    validateConfiguration();

    var now = Instant.now();
    var passwordHash = passwordEncoder.encode(password);
    mybatisClient
        .sql(
            "INSERT INTO admin_user (username, password_hash, display_name, status, created_at,"
                + " updated_at) VALUES (:username, :passwordHash, :displayName, 'ACTIVE', :now,"
                + " :now)")
        .param("username", username)
        .param("passwordHash", passwordHash)
        .param("displayName", displayName)
        .param("now", now)
        .update();
    var userId =
        mybatisClient
            .sql("SELECT id FROM admin_user WHERE username = :username")
            .param("username", username)
            .query(Long.class)
            .single();
    var roleId =
        mybatisClient
            .sql("SELECT id FROM admin_role WHERE role_code = 'ADMIN'")
            .query(Long.class)
            .optional()
            .orElseThrow(() -> new IllegalStateException("ADMIN 角色不存在"));
    mybatisClient
        .sql("INSERT INTO admin_user_role (user_id, role_id) VALUES (:userId, :roleId)")
        .param("userId", userId)
        .param("roleId", roleId)
        .update();
    log.warn("管理员账号初始化完成，请立即轮换初始密码，账号={}", username);
  }

  private void ensureSystemMenu() {
    if (!hasTable("admin_menu") || !hasTable("admin_role_menu")) {
      log.warn("基础平台菜单表未初始化，跳过菜单补偿；请由数据库发布流程执行基础平台升级 SQL 后再启用菜单管理");
      return;
    }
    var now = Instant.now();
    mybatisClient
        .sql(
            "INSERT IGNORE INTO admin_menu (parent_id, menu_code, menu_name, menu_type,"
                + " route_path, component_key, icon, sort_order, visible, status, created_at,"
                + " updated_at) VALUES (0, :menuCode, '菜单管理', 'PAGE', '/menus', 'menus',"
                + " 'Settings2', 93, TRUE, 'ACTIVE', :now, :now)")
        .param("menuCode", "system:menu")
        .param("now", now)
        .update();
    mybatisClient
        .sql(
            "INSERT IGNORE INTO admin_role_menu (role_id, menu_id) SELECT r.id, m.id FROM"
                + " admin_role r JOIN admin_menu m ON m.menu_code = :menuCode WHERE"
                + " r.role_code = 'ADMIN'")
        .param("menuCode", "system:menu")
        .update();
  }

  private void ensureAdminPermissions() {
    if (!hasTable("admin_role")
        || !hasTable("admin_permission")
        || !hasTable("admin_role_permission")) {
      log.warn("后台权限表未初始化，跳过 ADMIN 全量操作权限同步");
      return;
    }
    mybatisClient
        .sql(
            "INSERT IGNORE INTO admin_role_permission (role_id, permission_id) SELECT r.id, p.id"
                + " FROM admin_role r CROSS JOIN admin_permission p WHERE r.role_code = 'ADMIN'"
                + " AND p.status = 'ACTIVE'")
        .update();
  }

  private boolean hasTable(String tableName) {
    return mybatisClient
            .sql(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
                    + " AND table_name = :tableName")
            .param("tableName", tableName)
            .query(Long.class)
            .single()
        > 0;
  }

  private boolean hasAdminUser() {
    return mybatisClient.sql("SELECT COUNT(*) FROM admin_user").query(Long.class).single() > 0;
  }

  private void validateConfiguration() {
    if (isBlank(username) || isBlank(password) || isBlank(displayName)) {
      throw new IllegalStateException("管理员 Bootstrap 已启用，但初始化用户名、密码或显示名未配置");
    }
    if (password.length() < 12) {
      throw new IllegalStateException("管理员 Bootstrap 初始密码长度不能少于 12 位");
    }
  }

  private boolean isBlank(String value) {
    return Objects.isNull(value) || value.isBlank();
  }
}
