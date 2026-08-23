package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.OperationAuditQueryMapper;
import com.example.payments.platform.service.model.OperationAuditModel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationAuditQueryService {
  private final OperationAuditQueryMapper mapper;

  public Page list(String resourceType, String operatorId, int limit, int offset) {
    int safeLimit = Math.min(Math.max(limit, 1), 100);
    int safeOffset = Math.max(offset, 0);
    return new Page(
        mapper.select(resourceType, operatorId, safeLimit, safeOffset),
        mapper.count(resourceType, operatorId));
  }

  public record Page(List<OperationAuditModel> items, long total) {}
}
