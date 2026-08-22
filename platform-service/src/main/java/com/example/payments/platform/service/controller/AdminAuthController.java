package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import com.example.payments.platform.service.service.PlatformDataService;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/v1/auth")
public class AdminAuthController {
  private final PlatformDataService mybatisClient;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AdminAuthController(PlatformDataService mybatisClient, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.mybatisClient = mybatisClient;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    var user = mybatisClient.sql("SELECT id, username, password_hash, display_name FROM admin_user WHERE username = :username AND status = 'ACTIVE'")
        .param("username", request.username())
        .query(UserRecord.class)
        .optional()
        .orElseThrow(() -> unauthorized());
    if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
      throw unauthorized();
    }
    var roles = roles(user.id());
    return new LoginResponse(jwtService.create(user.username(), roles), "Bearer", jwtService.expirationSeconds(),
        new CurrentUser(user.username(), user.displayName(), roles));
  }

  @GetMapping("/me")
  public CurrentUser me(Authentication authentication) {
    var user = mybatisClient.sql("SELECT id, username, display_name FROM admin_user WHERE username = :username AND status = 'ACTIVE'")
        .param("username", authentication.getName())
        .query((rs, rowNum) -> new UserRecord(rs.getLong("id"), rs.getString("username"), "", rs.getString("display_name")))
        .single();
    return new CurrentUser(user.username(), user.displayName(), roles(user.id()));
  }

  private List<String> roles(long userId) {
    return mybatisClient.sql("SELECT r.role_code FROM admin_role r JOIN admin_user_role ur ON ur.role_id = r.id WHERE ur.user_id = :userId")
        .param("userId", userId)
        .query(String.class)
        .list();
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
  }

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
  public record LoginResponse(String accessToken, String tokenType, long expiresIn, CurrentUser user) {}
  public record CurrentUser(String username, String displayName, List<String> roles) {}
  public record UserRecord(long id, String username, String passwordHash, String displayName) {}
}
