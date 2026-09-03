package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.model.OperationAuditModel;
import com.example.payments.platform.service.service.OperationAuditQueryService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/audits")
@RequiredArgsConstructor
public class AdminAuditController {
  private final OperationAuditQueryService auditService;

  @GetMapping
  @PreAuthorize("hasAuthority('audit:list')")
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
    var result =
        auditService.list(
            blankToNull(resourceType), blankToNull(operatorId), size, (currentPage - 1) * size);
    return new AdminPageResponse<>(
        result.items().stream().map(AdminAuditController::response).toList(),
        currentPage,
        size,
        result.total());
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static AuditResponse response(OperationAuditModel value) {
    return new AuditResponse(
        value.auditId(),
        value.operatorId(),
        value.action(),
        value.resourceType(),
        value.resourceId(),
        value.requestId(),
        value.reason(),
        value.beforeSummary(),
        value.afterSummary(),
        value.createdAt());
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
