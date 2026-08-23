package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.model.UserModel;
import com.example.payments.platform.service.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {
  private final AdminUserService service;

  @GetMapping
  public AdminPageResponse<UserResponse> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    var r = service.list(page, pageSize);
    return new AdminPageResponse<>(
        r.items().stream().map(AdminUserController::response).toList(),
        r.page(),
        r.pageSize(),
        r.total());
  }

  @GetMapping("/{id}")
  public UserResponse detail(@PathVariable long id) {
    return response(service.detail(id));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse create(@Valid @RequestBody CreateUserRequest r, Authentication a) {
    return response(
        service.create(r.username(), r.password(), r.displayName(), r.roles(), a.getName(), r));
  }

  @PutMapping("/{id}")
  public UserResponse update(
      @PathVariable long id, @Valid @RequestBody UpdateUserRequest r, Authentication a) {
    return response(service.update(id, r.displayName(), r.roles(), a.getName(), r));
  }

  @PatchMapping("/{id}/status")
  public UserResponse changeStatus(
      @PathVariable long id, @Valid @RequestBody StatusRequest r, Authentication a) {
    return response(service.changeStatus(id, r.status(), a.getName(), r));
  }

  @PostMapping("/{id}/reset-password")
  public void resetPassword(
      @PathVariable long id, @Valid @RequestBody ResetPasswordRequest r, Authentication a) {
    service.resetPassword(id, r.newPassword(), a.getName(), r);
  }

  @PutMapping("/{id}/roles")
  public UserResponse updateRoles(
      @PathVariable long id, @Valid @RequestBody RoleUpdateRequest r, Authentication a) {
    return response(service.updateRoles(id, r.roles(), a.getName(), r));
  }

  private static UserResponse response(UserModel v) {
    return new UserResponse(v.id(), v.username(), v.displayName(), v.status(), v.roles());
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
