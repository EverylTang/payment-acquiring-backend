package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminAccessMapper;
import com.example.payments.platform.service.model.AccessModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAccessService {
  private final AdminAccessMapper mapper;
  private final AdminPermissionResolver permissionResolver;

  public AccessModel current(String username) {
    var roles = mapper.selectRoles(username);
    return new AccessModel(
        roles,
        mapper.selectMenus(username),
        permissionResolver.effectivePermissions(username, roles));
  }
}
