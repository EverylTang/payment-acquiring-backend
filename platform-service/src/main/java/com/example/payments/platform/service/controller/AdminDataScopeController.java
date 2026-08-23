package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminDataScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/data-scopes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDataScopeController {
  private final AdminDataScopeService service;

  @GetMapping("/roles/{roleCode}")
  public RoleScopeResponse role(@PathVariable String roleCode) {
    return new RoleScopeResponse(roleCode, service.role(roleCode));
  }

  @PutMapping("/roles/{roleCode}")
  public RoleScopeResponse updateRole(
      @PathVariable String roleCode,
      @Valid @RequestBody RoleScopeRequest request,
      Authentication a) {
    return new RoleScopeResponse(
        roleCode, service.updateRole(roleCode, request.scopeTypes(), a.getName(), request));
  }

  @GetMapping("/users/{userId}")
  public UserScopeResponse user(@PathVariable long userId) {
    return new UserScopeResponse(userId, service.user(userId));
  }

  @PutMapping("/users/{userId}")
  public UserScopeResponse updateUser(
      @PathVariable long userId, @Valid @RequestBody UserScopeRequest request, Authentication a) {
    return new UserScopeResponse(
        userId, service.updateUser(userId, request.merchantIds(), a.getName(), request));
  }

  public record RoleScopeResponse(String roleCode, List<String> scopeTypes) {}

  public record UserScopeResponse(long userId, List<String> merchantIds) {}

  public record RoleScopeRequest(
      @NotEmpty List<@Pattern(regexp = "ALL|ASSIGNED|SELF") String> scopeTypes) {}

  public record UserScopeRequest(@NotNull List<String> merchantIds) {}
}
