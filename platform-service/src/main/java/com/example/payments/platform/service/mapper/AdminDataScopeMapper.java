package com.example.payments.platform.service.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminDataScopeMapper {
  Long roleId(@Param("code") String code);
  List<String> roleScopes(@Param("roleId") long roleId);
  int clearRole(@Param("roleId") long roleId);
  int addRole(@Param("roleId") long roleId,@Param("scope") String scope);
  long userCount(@Param("userId") long userId);
  List<String> userMerchants(@Param("userId") long userId);
  long activeMerchantCount(@Param("ids") List<String> ids);
  int clearUser(@Param("userId") long userId);
  int addUser(@Param("userId") long userId,@Param("merchantId") String merchantId);
  boolean hasAllScope(@Param("username") String username);
  boolean allowed(@Param("username") String username,@Param("merchantId") String merchantId);
}
