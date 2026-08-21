package com.example.payments.platform.service.interfaces.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
  private final JdbcClient jdbcClient;
  private final PasswordEncoder passwordEncoder;

  public AdminUserController(JdbcClient jdbcClient, PasswordEncoder passwordEncoder) {
    this.jdbcClient = jdbcClient;
    this.passwordEncoder = passwordEncoder;
  }

  @GetMapping
  public List<UserResponse> list() {
    return jdbcClient.sql("SELECT u.id, u.username, u.display_name, u.status, GROUP_CONCAT(r.role_code ORDER BY r.role_code SEPARATOR ',') roles FROM admin_user u LEFT JOIN admin_user_role ur ON ur.user_id = u.id LEFT JOIN admin_role r ON r.id = ur.role_id GROUP BY u.id, u.username, u.display_name, u.status ORDER BY u.created_at DESC")
        .query((rs, rowNum) -> new UserResponse(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("status"), rs.getString("roles") == null ? List.of() : List.of(rs.getString("roles").split(",")))).list();
  }

  @PostMapping
  @Transactional
  public UserResponse create(@Valid @RequestBody CreateUserRequest request, Authentication authentication) {
    var now = Timestamp.from(Instant.now());
    jdbcClient.sql("INSERT INTO admin_user (username, password_hash, display_name, status, created_at, updated_at) VALUES (:username, :passwordHash, :displayName, 'ACTIVE', :now, :now)")
        .param("username", request.username()).param("passwordHash", passwordEncoder.encode(request.password())).param("displayName", request.displayName()).param("now", now).update();
    var userId = jdbcClient.sql("SELECT id FROM admin_user WHERE username = :username").param("username", request.username()).query(Long.class).single();
    replaceRoles(userId, request.roles());
    audit(authentication.getName(), "CREATE", request.username());
    return list().stream().filter(user -> user.username().equals(request.username())).findFirst().orElseThrow();
  }

  @PutMapping("/{id}")
  @Transactional
  public UserResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request, Authentication authentication) {
    jdbcClient.sql("UPDATE admin_user SET display_name = :displayName, updated_at = :now WHERE id = :id")
        .param("displayName", request.displayName()).param("now", Timestamp.from(Instant.now())).param("id", id).update();
    replaceRoles(id, request.roles());
    var username = username(id);
    audit(authentication.getName(), "UPDATE", username);
    return list().stream().filter(user -> user.id() == id).findFirst().orElseThrow();
  }

  @PatchMapping("/{id}/status")
  @Transactional
  public void changeStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request, Authentication authentication) {
    jdbcClient.sql("UPDATE admin_user SET status = :status, updated_at = :now WHERE id = :id")
        .param("status", request.status()).param("now", Timestamp.from(Instant.now())).param("id", id).update();
    audit(authentication.getName(), "CHANGE_STATUS", username(id));
  }

  private void replaceRoles(long userId, List<String> roles) {
    jdbcClient.sql("DELETE FROM admin_user_role WHERE user_id = :userId").param("userId", userId).update();
    roles.forEach(role -> jdbcClient.sql("INSERT INTO admin_user_role (user_id, role_id) SELECT :userId, id FROM admin_role WHERE role_code = :role")
        .param("userId", userId).param("role", role).update());
  }

  private String username(long id) {
    return jdbcClient.sql("SELECT username FROM admin_user WHERE id = :id").param("id", id).query(String.class).single();
  }

  private void audit(String operator, String action, String resourceId) {
    jdbcClient.sql("INSERT INTO operation_audit (audit_id, operator_id, action, resource_type, resource_id, created_at) VALUES (:audit, :operator, :action, 'ADMIN_USER', :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString()).param("operator", operator).param("action", action).param("resourceId", resourceId).param("now", Timestamp.from(Instant.now())).update();
  }

  public record CreateUserRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(min = 12, max = 128) String password, @NotBlank String displayName, List<String> roles) {}
  public record UpdateUserRequest(@NotBlank String displayName, List<String> roles) {}
  public record StatusRequest(@NotBlank String status) {}
  public record UserResponse(long id, String username, String displayName, String status, List<String> roles) {}
}
