package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminBootstrapMapper;
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

  private final AdminBootstrapMapper mapper;
  private final PasswordEncoder passwordEncoder;
  private final boolean enabled;
  private final String username;
  private final String password;
  private final String displayName;

  public AdminBootstrapRunner(
      AdminBootstrapMapper mapper,
      PasswordEncoder passwordEncoder,
      @Value("${platform.security.admin-bootstrap.enabled:true}") boolean enabled,
      @Value("${platform.security.admin-bootstrap.username:}") String username,
      @Value("${platform.security.admin-bootstrap.password:}") String password,
      @Value("${platform.security.admin-bootstrap.display-name:系统管理员}") String displayName) {
    this.mapper = mapper;
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
    mapper.insertAdminUser(username, passwordHash, displayName, now);
    var userId = mapper.selectUserId(username);
    var roleId = mapper.selectAdminRoleId();
    if (roleId == null) throw new IllegalStateException("ADMIN 角色不存在");
    mapper.insertUserRole(userId, roleId);
    log.warn("管理员账号初始化完成，请立即轮换初始密码，账号={}", username);
  }

  private void ensureSystemMenu() {
    if (!hasTable("admin_menu") || !hasTable("admin_role_menu")) {
      log.warn("基础平台菜单表未初始化，跳过菜单补偿；请由数据库发布流程执行基础平台升级 SQL 后再启用菜单管理");
      return;
    }
    var now = Instant.now();
    mapper.insertSystemMenu("system:menu", now);
    mapper.assignSystemMenuToAdmin("system:menu");
  }

  private void ensureAdminPermissions() {
    if (!hasTable("admin_role")
        || !hasTable("admin_permission")
        || !hasTable("admin_role_permission")) {
      log.warn("后台权限表未初始化，跳过 ADMIN 全量操作权限同步");
      return;
    }
    mapper.assignAllActivePermissionsToAdmin();
  }

  private boolean hasTable(String tableName) {
    return mapper.countTables(tableName) > 0;
  }

  private boolean hasAdminUser() {
    return mapper.countAdminUsers() > 0;
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
