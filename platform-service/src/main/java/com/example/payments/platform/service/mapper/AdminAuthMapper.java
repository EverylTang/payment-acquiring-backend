package com.example.payments.platform.service.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAuthMapper {
  UserRow findActiveUser(@Param("username") String username);
  UserRow findActiveUserWithoutPassword(@Param("username") String username);
  List<String> findRoles(@Param("userId") long userId);
  record UserRow(long id, String username, String passwordHash, String displayName) {}
}
