package com.example.payments.platform.service.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminPermissionCatalogMapper {
  List<MenuRow> menus();

  List<PermissionRow> permissions();

  record MenuRow(
      String menuCode,
      String menuName,
      long parentId,
      String menuType,
      String status,
      boolean visible,
      int sortOrder) {}

  record PermissionRow(
      String permissionCode, String permissionName, String resourceType, String status) {}
}
