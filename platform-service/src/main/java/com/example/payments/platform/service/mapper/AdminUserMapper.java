package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.UserRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserMapper {
  long countUsers();

  List<UserRow> selectUsers();

  UserRow selectById(@Param("id") long id);

  int insertUser(
      @Param("username") String username,
      @Param("passwordHash") String passwordHash,
      @Param("displayName") String displayName,
      @Param("now") Instant now);

  int updateUser(
      @Param("id") long id, @Param("displayName") String displayName, @Param("now") Instant now);

  int updateStatus(@Param("id") long id, @Param("status") String status, @Param("now") Instant now);

  int updatePassword(
      @Param("id") long id, @Param("passwordHash") String hash, @Param("now") Instant now);

  Long idByUsername(@Param("username") String username);

  String username(@Param("id") long id);

  long countRoles(@Param("id") long id);

  long countActiveAdminsExcept(@Param("id") long id);

  boolean isAdmin(@Param("id") long id);

  long countValidRoles(@Param("roles") List<String> roles);

  void deleteUserRoles(@Param("userId") long userId);

  void insertUserRole(@Param("userId") long userId, @Param("role") String role);
}
