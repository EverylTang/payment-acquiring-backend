package com.example.payments.platform.service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMerchantAccessService {
  private final AdminDataScopeService dataScopeService;

  public boolean hasAllScope(String username) {
    return dataScopeService.hasAllScope(username);
  }

  public void assertAllowed(Authentication authentication, String merchantId) {
    dataScopeService.assertAllowed(authentication.getName(), merchantId);
  }

}
