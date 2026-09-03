package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/roles")
@RequiredArgsConstructor
public class AdminRoleController {
  private final AdminRoleService service;

  @GetMapping
  @PreAuthorize("hasAuthority('system:role:list')")
  public AdminPageResponse<RoleResponse> list(
      @RequestParam(name = "page", defaultValue = "1") int page,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
    var r = service.list(page, pageSize);
    return new AdminPageResponse<>(
        r.items().stream().map(v -> new RoleResponse(v.id(), v.roleCode(), v.roleName())).toList(),
        r.page(),
        r.pageSize(),
        r.total());
  }

  @PostMapping
  @PreAuthorize("hasAuthority('system:role:create')")
  public RoleResponse create(@Valid @RequestBody CreateRoleRequest request, Authentication a) {
    var role =
        service.create(
            request.roleCode(),
            request.roleName(),
            request.menuCodes(),
            request.permissionCodes(),
            request.scopeTypes(),
            a.getName(),
            request);
    return new RoleResponse(role.id(), role.roleCode(), role.roleName());
  }

  @PutMapping("/{roleCode}")
  @PreAuthorize("hasAuthority('system:role:update')")
  public RoleResponse updateName(
      @PathVariable("roleCode") String roleCode,
      @Valid @RequestBody UpdateRoleRequest request,
      Authentication a) {
    var role = service.updateName(roleCode, request.roleName(), a.getName(), request);
    return new RoleResponse(role.id(), role.roleCode(), role.roleName());
  }

  @GetMapping("/{roleCode}/permissions")
  @PreAuthorize("hasAuthority('system:role:permission:list')")
  public RolePermissions permissions(@PathVariable("roleCode") String roleCode) {
    var p = service.permissions(roleCode);
    return new RolePermissions(p.menuCodes(), p.permissionCodes());
  }

  @PutMapping("/{roleCode}/permissions")
  @PreAuthorize("hasAuthority('system:role:permission:update')")
  public RolePermissions update(
      @PathVariable("roleCode") String roleCode,
      @Valid @RequestBody PermissionUpdateRequest r,
      Authentication a) {
    var p = service.update(roleCode, r.menuCodes(), r.permissionCodes(), a.getName(), r);
    return new RolePermissions(p.menuCodes(), p.permissionCodes());
  }

  public record RoleResponse(long id, String roleCode, String roleName) {}

  public record RolePermissions(List<String> menuCodes, List<String> permissionCodes) {}

  public record PermissionUpdateRequest(
      @NotNull List<String> menuCodes, @NotNull List<String> permissionCodes) {}

  public record CreateRoleRequest(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String roleCode,
      @NotBlank String roleName,
      @NotNull List<String> menuCodes,
      @NotNull List<String> permissionCodes,
      @NotNull List<@Pattern(regexp = "ALL|ASSIGNED|SELF") String> scopeTypes) {}

  public record UpdateRoleRequest(@NotBlank String roleName) {}
}
