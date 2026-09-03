package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.AdminAccessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
  @PreAuthorize("hasAuthority('system:access:list')")
  public AccessResponse current(Authentication authentication) {
    var value = accessService.current(authentication.getName());
    return new AccessResponse(
        value.roles(),
        value.menus().stream()
            .map(
                m ->
                    new MenuItem(
                        m.id(),
                        m.parentId(),
                        m.menuCode(),
                        m.menuName(),
                        m.menuType(),
                        m.routePath(),
                        m.componentKey(),
                        m.icon(),
                        m.sortOrder()))
            .toList(),
        value.permissions());
  }

  public record AccessResponse(
      List<String> roles, List<MenuItem> menus, List<String> permissions) {}

  public record MenuItem(
      long id,
      long parentId,
      String menuCode,
      String menuName,
      String menuType,
      String routePath,
      String componentKey,
      String icon,
      int sortOrder) {}
}
