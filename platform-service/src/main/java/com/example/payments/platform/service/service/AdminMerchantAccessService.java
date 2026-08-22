package com.example.payments.platform.service.service;

import lombok.RequiredArgsConstructor;

import com.example.payments.platform.service.mapper.MybatisPlusClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMerchantAccessService {
  private final MybatisPlusClient mybatisClient;

  public String predicate(Authentication authentication, String alias) {
    var username = authentication.getName();
    var hasAll =
        mybatisClient
                .sql(
                    "SELECT COUNT(*) FROM admin_role_data_scope ds JOIN admin_role r ON r.id ="
                        + " ds.role_id JOIN admin_user_role ur ON ur.role_id = r.id JOIN admin_user"
                        + " u ON u.id = ur.user_id WHERE u.username = :username AND u.status ="
                        + " 'ACTIVE' AND ds.scope_type = 'ALL'")
                .param("username", username)
                .query(Long.class)
                .single()
            > 0;
    if (hasAll) return "1=1";
    return alias
        + ".merchant_id IN (SELECT ums.merchant_id FROM admin_user_merchant_scope ums JOIN"
        + " admin_user u ON u.id = ums.user_id WHERE u.username = :scopeUsername)";
  }

  public MybatisPlusClient.StatementSpec bindScope( MybatisPlusClient.StatementSpec statement, Authentication authentication) {
    return statement.param("scopeUsername", authentication.getName());
  }

  public void assertAllowed(Authentication authentication, String merchantId) {
    var username = authentication.getName();
    var allowed =
        mybatisClient
            .sql(
                "SELECT COUNT(*) FROM merchant m WHERE m.merchant_id = :merchantId AND (EXISTS"
                    + " (SELECT 1 FROM admin_user_role ur JOIN admin_role_data_scope ds ON"
                    + " ds.role_id = ur.role_id JOIN admin_user u ON u.id = ur.user_id WHERE"
                    + " u.username = :username AND u.status = 'ACTIVE' AND ds.scope_type = 'ALL')"
                    + " OR EXISTS (SELECT 1 FROM admin_user_merchant_scope ums JOIN admin_user u ON"
                    + " u.id = ums.user_id WHERE u.username = :username AND ums.merchant_id ="
                    + " m.merchant_id))")
            .param("merchantId", merchantId)
            .param("username", username)
            .query(Long.class)
            .single();
    if (allowed == 0)
      throw new org.springframework.security.access.AccessDeniedException("无权访问该商户数据");
  }
}
