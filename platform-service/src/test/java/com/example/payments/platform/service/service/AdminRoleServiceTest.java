package com.example.payments.platform.service.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payments.platform.service.mapper.AdminRoleMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminRoleServiceTest {
  @Test
  void restoresAllActivePermissionsWhenUpdatingAdmin() {
    var mapper = mock(AdminRoleMapper.class);
    var service = new AdminRoleService(mapper, mock(OperationAuditService.class));
    when(mapper.id("ADMIN")).thenReturn(1L);
    when(mapper.activePermissions()).thenReturn(List.of("operation:a", "operation:b"));
    when(mapper.validMenus(List.of())).thenReturn(0L);
    when(mapper.validPermissions(List.of("operation:a", "operation:b"))).thenReturn(2L);
    when(mapper.menus(1L)).thenReturn(List.of());
    when(mapper.permissions(1L)).thenReturn(List.of("operation:a", "operation:b"));

    service.update("ADMIN", List.of(), List.of("operation:a"), "operator", null);

    verify(mapper).addPermission(1L, "operation:a");
    verify(mapper).addPermission(1L, "operation:b");
  }
}
