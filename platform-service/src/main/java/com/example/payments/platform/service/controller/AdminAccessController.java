package com.example.payments.platform.service.controller;

import lombok.RequiredArgsConstructor;

import com.example.payments.platform.service.service.AdminAccessService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/access")
@RequiredArgsConstructor
public class AdminAccessController {
  private final AdminAccessService accessService;

  @GetMapping
  public AccessResponse current(Authentication authentication) {
    var value = accessService.current(authentication.getName());
    return new AccessResponse(value.roles(), value.menus().stream().map(m -> new MenuItem(m.menuCode(), m.menuName(), m.menuType(), m.routePath(), m.componentKey(), m.icon(), m.sortOrder())).toList(), value.permissions());
  }

  public record AccessResponse( List<String> roles, List<MenuItem> menus, List<String> permissions) {}

  public record MenuItem(
      String menuCode,
      String menuName,
      String menuType,
      String routePath,
      String componentKey,
      String icon,
      int sortOrder) {}
}
