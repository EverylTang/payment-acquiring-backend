package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminDataScopeMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDataScopeService {
  private final AdminDataScopeMapper mapper;
  private final OperationAuditService audit;

  public List<String> role(String code) {
    return mapper.roleScopes(roleId(code));
  }

  @Transactional
  public List<String> updateRole(
      String code, List<String> scopes, String operator, Object payload) {
    long id = roleId(code);
    mapper.clearRole(id);
    scopes.forEach(s -> mapper.addRole(id, s));
    audit.record(operator, "UPDATE_ROLE_SCOPE", "DATA_SCOPE", code, payload);
    return role(code);
  }

  public List<String> user(long id) {
    ensureUser(id);
    return mapper.userMerchants(id);
  }

  @Transactional
  public List<String> updateUser(long id, List<String> merchants, String operator, Object payload) {
    ensureUser(id);
    if (mapper.activeMerchantCount(merchants) != merchants.size())
      throw new IllegalArgumentException("存在无效或停用商户");
    mapper.clearUser(id);
    merchants.forEach(m -> mapper.addUser(id, m));
    audit.record(operator, "UPDATE_USER_SCOPE", "DATA_SCOPE", String.valueOf(id), payload);
    return user(id);
  }

  public boolean hasAllScope(String username) {
    return mapper.hasAllScope(username);
  }

  public String predicate(String alias) {
    return alias
        + ".merchant_id IN (SELECT ums.merchant_id FROM admin_user_merchant_scope ums JOIN admin_user u ON u.id=ums.user_id WHERE u.username = :scopeUsername)";
  }

  public boolean allowed(String username, String merchantId) {
    return mapper.allowed(username, merchantId);
  }

  public void assertAllowed(String username, String merchantId) {
    if (!allowed(username, merchantId)) throw new AccessDeniedException("无权访问该商户数据");
  }

  private long roleId(String code) {
    var id = mapper.roleId(code);
    if (id == null) throw new IllegalArgumentException("角色不存在: " + code);
    return id;
  }

  private void ensureUser(long id) {
    if (mapper.userCount(id) == 0) throw new IllegalArgumentException("用户不存在: " + id);
  }
}
