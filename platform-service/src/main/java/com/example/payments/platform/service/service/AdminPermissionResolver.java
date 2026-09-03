package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminAccessMapper;
import java.util.List;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPermissionResolver {
  private final AdminAccessMapper mapper;

  public List<String> effectivePermissions(String username) {
    return effectivePermissions(username, mapper.selectRoles(username));
  }

  public List<String> effectivePermissions(String username, List<String> roles) {
    var permissions = new TreeSet<>(mapper.selectPermissions(username));
    if (roles.contains("ADMIN")) permissions.addAll(AdminOperationPermissionCatalog.all());
    return List.copyOf(permissions);
  }
}
