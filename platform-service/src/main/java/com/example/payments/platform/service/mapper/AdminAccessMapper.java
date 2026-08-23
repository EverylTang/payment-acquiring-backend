package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.AccessModel.MenuModel;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminAccessMapper {
  List<String> selectRoles(@Param("username") String username);
  List<MenuModel> selectMenus(@Param("username") String username);
  List<String> selectPermissions(@Param("username") String username);
}
