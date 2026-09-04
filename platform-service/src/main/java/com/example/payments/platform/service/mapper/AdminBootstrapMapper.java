package com.example.payments.platform.service.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminBootstrapMapper {
  int insertAdminUser(
      @Param("username") String username,
      @Param("passwordHash") String passwordHash,
      @Param("displayName") String displayName,
      @Param("now") Instant now);

  Long selectUserId(@Param("username") String username);

  Long selectAdminRoleId();

  int insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

  int insertSystemMenu(@Param("menuCode") String menuCode, @Param("now") Instant now);

  int assignSystemMenuToAdmin(@Param("menuCode") String menuCode);

  int assignAllActivePermissionsToAdmin();

  long countTables(@Param("tableName") String tableName);

  long countAdminUsers();
}
