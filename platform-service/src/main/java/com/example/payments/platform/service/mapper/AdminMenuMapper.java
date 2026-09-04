package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.service.AdminMenuService.MenuResponse;
import com.example.payments.platform.service.service.AdminMenuService.PermissionResponse;
import com.example.payments.platform.service.service.AdminMenuService.ResourceTypeResponse;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMenuMapper {
  long count(@Param("menuName") String menuName, @Param("menuCode") String menuCode, @Param("menuType") String menuType, @Param("status") String status);
  List<MenuResponse> selectPage(@Param("menuName") String menuName, @Param("menuCode") String menuCode, @Param("menuType") String menuType, @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);
  int insertMenu(@Param("parentId") long parentId, @Param("code") String code, @Param("name") String name, @Param("type") String type, @Param("path") String path, @Param("component") String component, @Param("icon") String icon, @Param("sort") int sort, @Param("visible") boolean visible, @Param("now") Instant now);
  int updateStatus(@Param("status") String status, @Param("now") Instant now, @Param("code") String code);
  int updateMenu(@Param("parentId") long parentId, @Param("name") String name, @Param("type") String type, @Param("path") String path, @Param("component") String component, @Param("icon") String icon, @Param("sort") int sort, @Param("visible") boolean visible, @Param("now") Instant now, @Param("code") String code);
  long countChildren(@Param("parentId") long parentId);
  int deleteRolePermissionsByPrefix(@Param("prefix") String prefix);
  int deletePermissionsByPrefix(@Param("prefix") String prefix);
  int deleteRoleMenus(@Param("menuId") long menuId);
  int deleteMenuResourceTypes(@Param("menuId") long menuId);
  int deleteMenu(@Param("menuId") long menuId);
  List<PermissionResponse> selectPermissions(@Param("prefix") String prefix);
  List<ResourceTypeResponse> selectActiveResourceTypes();
  int insertResourceType(@Param("type") String type, @Param("name") String name);
  List<String> selectMenuResourceTypes(@Param("menuId") long menuId);
  int insertPermission(@Param("code") String code, @Param("name") String name, @Param("type") String type, @Param("now") Instant now);
  int updatePermission(@Param("name") String name, @Param("type") String type, @Param("status") String status, @Param("now") Instant now, @Param("code") String code);
  int deleteRolePermission(@Param("code") String code);
  int deletePermission(@Param("code") String code);
  Long selectMenuId(@Param("code") String code);
  MenuResponse selectMenu(@Param("code") String code);
  PermissionResponse selectPermission(@Param("code") String code);
  int insertMenuResourceType(@Param("menuId") long menuId, @Param("type") String type);
}
