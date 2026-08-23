package com.example.payments.platform.service.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationAuditMapper {
  int insertAudit(@Param("auditId") String auditId,@Param("operator") String operator,@Param("action") String action,
      @Param("resourceType") String resourceType,@Param("resourceId") String resourceId,@Param("summary") String summary,@Param("createdAt") Instant createdAt);
}
