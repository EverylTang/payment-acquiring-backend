package com.example.payments.platform.service.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAuthMapper {
  UserRow findActiveUser(@Param("username") String username);

  UserRow findActiveUserWithoutPassword(@Param("username") String username);

  List<String> findRoles(@Param("userId") long userId);

  int updatePassword(
      @Param("id") long id, @Param("passwordHash") String passwordHash, @Param("now") Instant now);

  record UserRow(long id, String username, String passwordHash, String displayName) {}
}
