package com.example.payments.platform.service.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.payments.platform.service.mapper.AdminAccessMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminPermissionResolverTest {
  @Test
  void grantsEveryStandardOperationToAdmin() {
    var mapper = mock(AdminAccessMapper.class);
    var resolver = new AdminPermissionResolver(mapper);
    when(mapper.selectPermissions("admin")).thenReturn(List.of("merchant:list"));

    var permissions = resolver.effectivePermissions("admin", List.of("ADMIN"));

    assertTrue(permissions.contains("merchant:list"));
    assertTrue(permissions.contains("outbox:redrive"));
    assertTrue(permissions.contains("reconciliation:difference:resolve"));
  }

  @Test
  void doesNotGrantAdminOperationsToOtherRoles() {
    var mapper = mock(AdminAccessMapper.class);
    var resolver = new AdminPermissionResolver(mapper);
    when(mapper.selectPermissions("operator")).thenReturn(List.of("merchant:list"));

    var permissions = resolver.effectivePermissions("operator", List.of("OPS"));

    assertTrue(permissions.contains("merchant:list"));
    assertFalse(permissions.contains("outbox:redrive"));
  }
}
