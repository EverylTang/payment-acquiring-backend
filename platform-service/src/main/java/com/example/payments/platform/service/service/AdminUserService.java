package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminUserMapper;
import com.example.payments.platform.service.model.UserModel;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {
  private final AdminUserMapper mapper;
  private final PasswordEncoder encoder;
  private final OperationAuditService audit;
  public Page list(int page,int size){int p=Math.max(page,1),s=Math.min(Math.max(size,1),100);return new Page(mapper.selectUsers().stream().skip((long)(p-1)*s).limit(s).toList(),p,s,mapper.countUsers());}
  public UserModel detail(long id){var v=mapper.selectById(id);if(v==null)throw new IllegalArgumentException("用户不存在: "+id);return normalize(v);}
  @Transactional public UserModel create(String username,String password,String displayName,List<String> roles,String operator,Object payload){validateRoles(roles);mapper.insertUser(username,encoder.encode(password),displayName,Instant.now());long id=mapper.idByUsername(username);replaceRoles(id,roles);audit.record(operator,"CREATE","ADMIN_USER",username,payload);return detail(id);}
  @Transactional public UserModel update(long id,String displayName,List<String> roles,String operator,Object payload){validateRoles(roles);ensureAdminPreserved(id,roles);mapper.updateUser(id,displayName,Instant.now());replaceRoles(id,roles);audit.record(operator,"UPDATE","ADMIN_USER",mapper.username(id),payload);return detail(id);}
  @Transactional public UserModel changeStatus(long id,String status,String operator,Object payload){if("DISABLED".equals(status)&&mapper.countActiveAdminsExcept(id)==0)throw new IllegalStateException("不能禁用最后一个有效系统管理员");mapper.updateStatus(id,status,Instant.now());audit.record(operator,"CHANGE_STATUS","ADMIN_USER",mapper.username(id),payload);return detail(id);}
  @Transactional public void resetPassword(long id,String password,String operator,Object payload){detail(id);mapper.updatePassword(id,encoder.encode(password),Instant.now());audit.record(operator,"RESET_PASSWORD","ADMIN_USER",mapper.username(id),payload);}
  @Transactional public UserModel updateRoles(long id,List<String> roles,String operator,Object payload){var username=mapper.username(id);if(username==null)throw new IllegalArgumentException("用户不存在: "+id);validateRoles(roles);ensureAdminPreserved(id,roles);replaceRoles(id,roles);audit.record(operator,"UPDATE_ROLES","ADMIN_USER",username,payload);return detail(id);}
  private UserModel normalize(UserModel v){return new UserModel(v.id(),v.username(),v.displayName(),v.status(),v.roles()==null||v.roles().isEmpty()?List.of():List.of(v.roles().get(0).split(",")));}
  private void replaceRoles(long id,List<String> roles){mapper.deleteUserRoles(id);roles.forEach(r->mapper.insertUserRole(id,r));}
  private void validateRoles(List<String> roles){if(roles==null||roles.isEmpty()||roles.size()!=new HashSet<>(roles).size()||mapper.countValidRoles(roles)!=roles.size())throw new IllegalArgumentException("存在无效或重复角色");}
  private void ensureAdminPreserved(long id,List<String> roles){if(roles.contains("ADMIN")||!mapper.isAdmin(id)||mapper.countActiveAdminsExcept(id)>0)return;throw new IllegalStateException("不能移除最后一个有效系统管理员的 ADMIN 角色");}
  public record Page(List<UserModel> items,int page,int pageSize,long total){}
}
