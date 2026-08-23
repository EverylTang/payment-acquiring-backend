package com.example.payments.platform.service.model;

import java.util.List;

public record AccessModel(List<String> roles, List<MenuModel> menus, List<String> permissions) {
  public record MenuModel(
      String menuCode,
      String menuName,
      String menuType,
      String routePath,
      String componentKey,
      String icon,
      int sortOrder) {}
}
