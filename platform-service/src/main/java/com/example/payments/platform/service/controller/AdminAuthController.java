package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/v1/auth")
@RequiredArgsConstructor
public class AdminAuthController {
  private final AdminAuthService authService;

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    try {
      var result = authService.login(request.username(), request.password());
      return new LoginResponse(
          result.token(),
          "Bearer",
          result.expiresIn(),
          new CurrentUser(
              result.user().username(), result.user().displayName(), result.user().roles()));
    } catch (AdminAuthService.UnauthorizedException e) {
      throw unauthorized();
    }
  }

  @GetMapping("/me")
  public CurrentUser me(Authentication authentication) {
    try {
      var user = authService.current(authentication.getName());
      return new CurrentUser(user.username(), user.displayName(), user.roles());
    } catch (AdminAuthService.UnauthorizedException e) {
      throw unauthorized();
    }
  }

  @PostMapping("/change-password")
  public void changePassword(
      @Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
    try {
      authService.changePassword(
          authentication.getName(), request.currentPassword(), request.newPassword());
    } catch (AdminAuthService.UnauthorizedException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
    }
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword, @NotBlank @Size(min = 12, max = 128) String newPassword) {}

  public record LoginResponse(
      String accessToken, String tokenType, long expiresIn, CurrentUser user) {}

  public record CurrentUser(String username, String displayName, List<String> roles) {}
}
