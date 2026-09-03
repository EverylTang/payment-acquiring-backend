package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminAuthMapper;
import com.example.payments.platform.service.security.JwtService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAuthService {
  private final AdminAuthMapper mapper;
  private final AdminPermissionResolver permissionResolver;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthResult login(String username, String password) {
    var user = mapper.findActiveUser(username);
    if (user == null || !passwordEncoder.matches(password, user.passwordHash()))
      throw new UnauthorizedException();
    var roles = mapper.findRoles(user.id());
    return new AuthResult(
        jwtService.create(
            user.username(), permissionResolver.effectivePermissions(user.username(), roles)),
        jwtService.expirationSeconds(),
        new CurrentUser(user.username(), user.displayName(), roles));
  }

  public CurrentUser current(String username) {
    var user = mapper.findActiveUserWithoutPassword(username);
    if (user == null) throw new UnauthorizedException();
    return new CurrentUser(user.username(), user.displayName(), mapper.findRoles(user.id()));
  }

  public void changePassword(String username, String currentPassword, String newPassword) {
    var user = mapper.findActiveUser(username);
    if (user == null || !passwordEncoder.matches(currentPassword, user.passwordHash())) {
      throw new UnauthorizedException();
    }
    mapper.updatePassword(user.id(), passwordEncoder.encode(newPassword), Instant.now());
  }

  public record AuthResult(String token, long expiresIn, CurrentUser user) {}

  public record CurrentUser(String username, String displayName, List<String> roles) {}

  public static class UnauthorizedException extends RuntimeException {}
}
