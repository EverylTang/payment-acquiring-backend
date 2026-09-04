package com.example.payments.platform.service.mapper;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationAuditMapper {
  int insertAudit(
      @Param("auditId") String auditId,
      @Param("operator") String operator,
      @Param("action") String action,
      @Param("resourceType") String resourceType,
      @Param("resourceId") String resourceId,
      @Param("summary") String summary,
      @Param("createdAt") Instant createdAt);

  int insertAuditWithoutSummary(
      @Param("auditId") String auditId,
      @Param("operator") String operator,
      @Param("action") String action,
      @Param("resourceType") String resourceType,
      @Param("resourceId") String resourceId,
      @Param("createdAt") Instant createdAt);

  int insertAuditWithReason(
      @Param("auditId") String auditId,
      @Param("operator") String operator,
      @Param("action") String action,
      @Param("resourceType") String resourceType,
      @Param("resourceId") String resourceId,
      @Param("reason") String reason,
      @Param("summary") String summary,
      @Param("createdAt") Instant createdAt);
}
