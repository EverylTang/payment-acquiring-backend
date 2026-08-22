package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.PlatformDataService;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/audits")
@PreAuthorize("hasAnyRole('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY')")
public class AdminAuditController {
  private final PlatformDataService mybatisClient;

  public AdminAuditController(PlatformDataService mybatisClient) {
    this.mybatisClient = mybatisClient;
  }

  @GetMapping
  public AdminPageResponse<AuditResponse> list(
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) String operatorId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var where =
        "WHERE (:resourceType IS NULL OR resource_type = :resourceType) AND (:operatorId IS NULL OR"
            + " operator_id = :operatorId)";
    var total =
        mybatisClient
            .sql("SELECT COUNT(*) FROM operation_audit " + where)
            .param("resourceType", blankToNull(resourceType))
            .param("operatorId", blankToNull(operatorId))
            .query(Long.class)
            .single();
    var items =
        mybatisClient
            .sql(
                "SELECT audit_id, operator_id, action, resource_type, resource_id, request_id,"
                    + " reason, before_summary, after_summary, created_at FROM operation_audit "
                    + where
                    + " ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
            .param("resourceType", blankToNull(resourceType))
            .param("operatorId", blankToNull(operatorId))
            .param("limit", size)
            .param("offset", (currentPage - 1) * size)
            .query(AuditResponse.class)
            .list();
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record AuditResponse(
      String auditId,
      String operatorId,
      String action,
      String resourceType,
      String resourceId,
      String requestId,
      String reason,
      String beforeSummary,
      String afterSummary,
      Instant createdAt) {}
}
