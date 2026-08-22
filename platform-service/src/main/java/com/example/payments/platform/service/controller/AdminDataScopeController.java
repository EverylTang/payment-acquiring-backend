package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.PlatformDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/data-scopes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDataScopeController {
  private final PlatformDataService mybatisClient;

  public AdminDataScopeController(PlatformDataService mybatisClient) {
    this.mybatisClient = mybatisClient;
  }

  @GetMapping("/roles/{roleCode}")
  public RoleScopeResponse role(@PathVariable String roleCode) {
    var roleId = roleId(roleCode);
    return new RoleScopeResponse(
        roleCode,
        mybatisClient
            .sql(
                "SELECT scope_type FROM admin_role_data_scope WHERE role_id = :roleId ORDER BY"
                    + " scope_type")
            .param("roleId", roleId)
            .query(String.class)
            .list());
  }

  @PutMapping("/roles/{roleCode}")
  @Transactional
  public RoleScopeResponse updateRole(
      @PathVariable String roleCode,
      @Valid @RequestBody RoleScopeRequest request,
      Authentication authentication) {
    var roleId = roleId(roleCode);
    mybatisClient
        .sql("DELETE FROM admin_role_data_scope WHERE role_id = :roleId")
        .param("roleId", roleId)
        .update();
    request
        .scopeTypes()
        .forEach(
            scope ->
                mybatisClient
                    .sql(
                        "INSERT INTO admin_role_data_scope (role_id, scope_type) VALUES (:roleId,"
                            + " :scope)")
                    .param("roleId", roleId)
                    .param("scope", scope)
                    .update());
    audit(authentication.getName(), "UPDATE_ROLE_SCOPE", roleCode);
    return role(roleCode);
  }

  @GetMapping("/users/{userId}")
  public UserScopeResponse user(@PathVariable long userId) {
    return new UserScopeResponse(
        userId,
        mybatisClient
            .sql(
                "SELECT merchant_id FROM admin_user_merchant_scope WHERE user_id = :userId ORDER BY"
                    + " merchant_id")
            .param("userId", userId)
            .query(String.class)
            .list());
  }

  @PutMapping("/users/{userId}")
  @Transactional
  public UserScopeResponse updateUser(
      @PathVariable long userId,
      @Valid @RequestBody UserScopeRequest request,
      Authentication authentication) {
    if (mybatisClient
            .sql("SELECT COUNT(*) FROM admin_user WHERE id = :userId")
            .param("userId", userId)
            .query(Long.class)
            .single()
        == 0) throw new IllegalArgumentException("用户不存在: " + userId);
    var active =
        mybatisClient
            .sql(
                "SELECT COUNT(*) FROM merchant WHERE merchant_id IN (:merchantIds) AND status ="
                    + " 'ACTIVE'")
            .param("merchantIds", request.merchantIds())
            .query(Long.class)
            .single();
    if (active != request.merchantIds().size()) throw new IllegalArgumentException("存在无效或停用商户");
    mybatisClient
        .sql("DELETE FROM admin_user_merchant_scope WHERE user_id = :userId")
        .param("userId", userId)
        .update();
    request
        .merchantIds()
        .forEach(
            merchantId ->
                mybatisClient
                    .sql(
                        "INSERT INTO admin_user_merchant_scope (user_id, merchant_id) VALUES"
                            + " (:userId, :merchantId)")
                    .param("userId", userId)
                    .param("merchantId", merchantId)
                    .update());
    audit(authentication.getName(), "UPDATE_USER_SCOPE", String.valueOf(userId));
    return user(userId);
  }

  private long roleId(String roleCode) {
    return mybatisClient
        .sql("SELECT id FROM admin_role WHERE role_code = :roleCode")
        .param("roleCode", roleCode)
        .query(Long.class)
        .single();
  }

  private void audit(String operator, String action, String resourceId) {
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, created_at) VALUES (:audit, :operator, :action, 'DATA_SCOPE',"
                + " :resourceId, :now)")
        .param("audit", UUID.randomUUID().toString())
        .param("operator", operator)
        .param("action", action)
        .param("resourceId", resourceId)
        .param("now", Instant.now())
        .update();
  }

  public record RoleScopeResponse(String roleCode, List<String> scopeTypes) {}

  public record UserScopeResponse(long userId, List<String> merchantIds) {}

  public record RoleScopeRequest( @NotEmpty List<@Pattern(regexp = "ALL|ASSIGNED|SELF") String> scopeTypes) {}

  public record UserScopeRequest(@NotNull List<String> merchantIds) {}
}
