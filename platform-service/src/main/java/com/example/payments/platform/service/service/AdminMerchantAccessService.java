package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.MybatisPlusClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMerchantAccessService {
  private final AdminDataScopeService dataScopeService;
  public boolean hasAllScope(String username) { return dataScopeService.hasAllScope(username); }
  public String predicate(Authentication authentication, String alias) {
    return dataScopeService.hasAllScope(authentication.getName()) ? "1=1" : dataScopeService.predicate(alias);
  }
  public void assertAllowed(Authentication authentication, String merchantId) { dataScopeService.assertAllowed(authentication.getName(), merchantId); }
  public MybatisPlusClient.StatementSpec bindScope(MybatisPlusClient.StatementSpec statement, Authentication authentication) { return statement.param("scopeUsername", authentication.getName()); }
}
