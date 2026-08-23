package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/roles")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRoleController {
  private final AdminRoleService service;

  @GetMapping
  public AdminPageResponse<RoleResponse> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    var r = service.list(page, pageSize);
    return new AdminPageResponse<>(
        r.items().stream().map(v -> new RoleResponse(v.id(), v.roleCode(), v.roleName())).toList(),
        r.page(),
        r.pageSize(),
        r.total());
  }

  @GetMapping("/{roleCode}/permissions")
  public RolePermissions permissions(@PathVariable String roleCode) {
    var p = service.permissions(roleCode);
    return new RolePermissions(p.menuCodes(), p.permissionCodes());
  }

  @PutMapping("/{roleCode}/permissions")
  public RolePermissions update(
      @PathVariable String roleCode,
      @Valid @RequestBody PermissionUpdateRequest r,
      Authentication a) {
    var p = service.update(roleCode, r.menuCodes(), r.permissionCodes(), a.getName(), r);
    return new RolePermissions(p.menuCodes(), p.permissionCodes());
  }

  public record RoleResponse(long id, String roleCode, String roleName) {}

  public record RolePermissions(List<String> menuCodes, List<String> permissionCodes) {}

  public record PermissionUpdateRequest(
      @NotNull List<String> menuCodes, @NotNull List<String> permissionCodes) {}
}
