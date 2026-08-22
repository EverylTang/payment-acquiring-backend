package com.example.payments.platform.service.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.example.payments.platform.service.mapper.MybatisPlusClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
  private final MybatisPlusClient mybatisClient;
  private final PasswordEncoder passwordEncoder;

  public AdminUserController(MybatisPlusClient mybatisClient, PasswordEncoder passwordEncoder) {
    this.mybatisClient = mybatisClient;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping
  public AdminPageResponse<UserResponse> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var offset = (currentPage - 1) * size;
    var total = mybatisClient.sql("SELECT COUNT(*) FROM admin_user").query(Long.class).single();
    var items = all().stream().skip(offset).limit(size).toList();
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  private List<UserResponse> all() {
    return mybatisClient
        .sql(
            "SELECT u.id, u.username, u.display_name, u.status, GROUP_CONCAT(r.role_code ORDER BY"
                + " r.role_code SEPARATOR ',') roles FROM admin_user u LEFT JOIN admin_user_role ur"
                + " ON ur.user_id = u.id LEFT JOIN admin_role r ON r.id = ur.role_id GROUP BY u.id,"
                + " u.username, u.display_name, u.status ORDER BY u.created_at DESC")
        .query(
            (rs, rowNum) ->
                new UserResponse(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    rs.getString("status"),
                    rs.getString("roles") == null
                        ? List.of()
                        : List.of(rs.getString("roles").split(","))))
        .list();
  }

  @GetMapping("/{id}")
  public UserResponse detail(@PathVariable long id) {
    return all().stream()
        .filter(user -> user.id() == id)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
  }

  @PostMapping
  @Transactional
  public UserResponse create(
      @Valid @RequestBody CreateUserRequest request, Authentication authentication) {
    validateRoles(request.roles());
    var now = Instant.now();
    mybatisClient
        .sql(
            "INSERT INTO admin_user (username, password_hash, display_name, status, created_at,"
                + " updated_at) VALUES (:username, :passwordHash, :displayName, 'ACTIVE', :now,"
                + " :now)")
        .param("username", request.username())
        .param("passwordHash", passwordEncoder.encode(request.password()))
        .param("displayName", request.displayName())
        .param("now", now)
        .update();
    var userId =
        mybatisClient
            .sql("SELECT id FROM admin_user WHERE username = :username")
            .param("username", request.username())
            .query(Long.class)
            .single();
    replaceRoles(userId, request.roles());
    audit(authentication.getName(), "CREATE", request.username());
    return detail(userId);
  }

  @PutMapping("/{id}")
  @Transactional
  public UserResponse update(
      @PathVariable long id,
      @Valid @RequestBody UpdateUserRequest request,
      Authentication authentication) {
    validateRoles(request.roles());
    ensureAdminPreserved(id, request.roles());
    mybatisClient
        .sql("UPDATE admin_user SET display_name = :displayName, updated_at = :now WHERE id = :id")
        .param("displayName", request.displayName())
        .param("now", Instant.now())
        .param("id", id)
        .update();
    replaceRoles(id, request.roles());
    var username = username(id);
    audit(authentication.getName(), "UPDATE", username);
    return detail(id);
  }

  @PatchMapping("/{id}/status")
  @Transactional
  public UserResponse changeStatus(
      @PathVariable long id,
      @Valid @RequestBody StatusRequest request,
      Authentication authentication) {
    if ("DISABLED".equals(request.status()) && isLastActiveAdmin(id)) {
      throw new IllegalStateException("不能禁用最后一个有效系统管理员");
    }
    mybatisClient
        .sql("UPDATE admin_user SET status = :status, updated_at = :now WHERE id = :id")
        .param("status", request.status())
        .param("now", Instant.now())
        .param("id", id)
        .update();
    audit(authentication.getName(), "CHANGE_STATUS", username(id));
    return detail(id);
  }

  @PostMapping("/{id}/reset-password")
  @Transactional
  public void resetPassword(
      @PathVariable long id,
      @Valid @RequestBody ResetPasswordRequest request,
      Authentication authentication) {
    mybatisClient
        .sql(
            "UPDATE admin_user SET password_hash = :passwordHash, updated_at = :now WHERE id = :id")
        .param("passwordHash", passwordEncoder.encode(request.newPassword()))
        .param("now", Instant.now())
        .param("id", id)
        .update();
    audit(authentication.getName(), "RESET_PASSWORD", username(id));
  }

  @PutMapping("/{id}/roles")
  @Transactional
  public UserResponse updateRoles(
      @PathVariable long id,
      @Valid @RequestBody RoleUpdateRequest request,
      Authentication authentication) {
    username(id);
    validateRoles(request.roles());
    ensureAdminPreserved(id, request.roles());
    replaceRoles(id, request.roles());
    audit(authentication.getName(), "UPDATE_ROLES", username(id));
    return detail(id);
  }

  private void replaceRoles(long userId, List<String> roles) {
    mybatisClient
        .sql("DELETE FROM admin_user_role WHERE user_id = :userId")
        .param("userId", userId)
        .update();
    roles.forEach(
        role ->
            mybatisClient
                .sql(
                    "INSERT INTO admin_user_role (user_id, role_id) SELECT :userId, id FROM"
                        + " admin_role WHERE role_code = :role")
                .param("userId", userId)
                .param("role", role)
                .update());
  }

  private String username(long id) {
    return mybatisClient
        .sql("SELECT username FROM admin_user WHERE id = :id")
        .param("id", id)
        .query(String.class)
        .single();
  }

  private boolean isLastActiveAdmin(long id) {
    return mybatisClient
            .sql(
                "SELECT COUNT(*) FROM admin_user u JOIN admin_user_role ur ON ur.user_id = u.id"
                    + " JOIN admin_role r ON r.id = ur.role_id WHERE u.status = 'ACTIVE' AND"
                    + " r.role_code = 'ADMIN' AND u.id <> :id")
            .param("id", id)
            .query(Long.class)
            .single()
        == 0;
  }

  private void validateRoles(List<String> roles) {
    if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("至少需要分配一个角色");
    var count =
        mybatisClient
            .sql("SELECT COUNT(*) FROM admin_role WHERE role_code IN (:roles)")
            .param("roles", roles)
            .query(Long.class)
            .single();
    if (count != roles.stream().distinct().count()) throw new IllegalArgumentException("存在无效或重复角色");
  }

  private void ensureAdminPreserved(long id, List<String> roles) {
    if (roles.contains("ADMIN") || !isAdmin(id)) return;
    if (isLastActiveAdmin(id)) throw new IllegalStateException("不能移除最后一个有效系统管理员的 ADMIN 角色");
  }

  private boolean isAdmin(long id) {
    return mybatisClient
            .sql(
                "SELECT COUNT(*) FROM admin_user_role ur JOIN admin_role r ON r.id = ur.role_id"
                    + " WHERE ur.user_id = :id AND r.role_code = 'ADMIN'")
            .param("id", id)
            .query(Long.class)
            .single()
        > 0;
  }

  private void audit(String operator, String action, String resourceId) {
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, created_at) VALUES (:audit, :operator, :action, 'ADMIN_USER',"
                + " :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", operator)
        .param("action", action)
        .param("resourceId", resourceId)
        .param("now", Instant.now())
        .update();
  }

  public record CreateUserRequest(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Size(min = 12, max = 128) String password,
      @NotBlank String displayName,
      List<String> roles) {}

  public record UpdateUserRequest(@NotBlank String displayName, List<String> roles) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}

  public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 128) String newPassword) {}

  public record RoleUpdateRequest(List<String> roles) {}

  public record UserResponse(
      long id, String username, String displayName, String status, List<String> roles) {}
}
