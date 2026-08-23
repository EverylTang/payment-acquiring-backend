package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.AdminAccessMapper;
import com.example.payments.platform.service.model.AccessModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminAccessService {
  private final AdminAccessMapper mapper;
  public AccessModel current(String username) {
    return new AccessModel(mapper.selectRoles(username), mapper.selectMenus(username), mapper.selectPermissions(username));
  }
}
