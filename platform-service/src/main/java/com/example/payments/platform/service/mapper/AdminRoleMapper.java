package com.example.payments.platform.service.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminRoleMapper {
  long count();

  List<RoleRow> page(@Param("limit") int limit, @Param("offset") int offset);

  Long id(@Param("code") String code);

  List<String> permissions(@Param("id") long id);

  List<String> menus(@Param("id") long id);

  long validMenus(@Param("codes") List<String> codes);

  long validPermissions(@Param("codes") List<String> codes);

  void clearMenus(@Param("id") long id);

  void clearPermissions(@Param("id") long id);

  void addMenu(@Param("id") long id, @Param("code") String code);

  void addPermission(@Param("id") long id, @Param("code") String code);

  record RoleRow(long id, String roleCode, String roleName) {}
}
