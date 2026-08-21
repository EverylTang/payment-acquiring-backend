package com.example.payments.platform.service.interfaces.rest;

import com.example.payments.platform.service.application.ConfigurationSnapshotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/v1/config-releases")
public class ConfigReleaseController {
  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationSnapshotService snapshotService;

  public ConfigReleaseController(JdbcClient jdbcClient, ObjectMapper objectMapper, ConfigurationSnapshotService snapshotService) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
    this.snapshotService = snapshotService;
  }

  @GetMapping
  public List<ReleaseResponse> list() {
    return jdbcClient.sql("SELECT release_id, version_no, status, created_by, approved_by, published_at, created_at FROM config_release ORDER BY version_no DESC")
        .query(ReleaseResponse.class)
        .list();
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public ReleaseResponse create(@Valid @RequestBody CreateReleaseRequest request, Authentication authentication) {
    var version = jdbcClient.sql("SELECT COALESCE(MAX(version_no), 0) + 1 FROM config_release FOR UPDATE")
        .query(Long.class).single();
    var releaseId = "release-" + UUID.randomUUID();
    jdbcClient.sql("INSERT INTO config_release (release_id, version_no, status, config_json, created_by, created_at) VALUES (:releaseId, :version, 'DRAFT', :config, :createdBy, :createdAt)")
        .param("releaseId", releaseId).param("version", version).param("config", json(request.configuration()))
        .param("createdBy", authentication.getName()).param("createdAt", Timestamp.from(Instant.now())).update();
    audit(authentication.getName(), "CREATE", releaseId, request.reason(), request.configuration());
    return find(releaseId);
  }

  @PostMapping("/{releaseId}/submit")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public ReleaseResponse submit(@PathVariable String releaseId, @Valid @RequestBody ReasonRequest request, Authentication authentication) {
    var release = find(releaseId);
    var errors = snapshotService.validate(release.versionNo());
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("；", errors));
    }
    transition(releaseId, "DRAFT", "IN_REVIEW", null);
    audit(authentication.getName(), "SUBMIT", releaseId, request.reason(), Map.of("status", "IN_REVIEW"));
    return find(releaseId);
  }

  @PostMapping("/{releaseId}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ReleaseResponse approve(@PathVariable String releaseId, @RequestBody ReasonRequest request, Authentication authentication) {
    transition(releaseId, "IN_REVIEW", "APPROVED", authentication.getName());
    audit(authentication.getName(), "APPROVE", releaseId, request.reason(), Map.of("status", "APPROVED"));
    return find(releaseId);
  }

  @PostMapping("/{releaseId}/publish")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ReleaseResponse publish(@PathVariable String releaseId, @RequestBody ReasonRequest request, Authentication authentication) {
    var publishedAt = Timestamp.from(Instant.now());
    var updated = jdbcClient.sql("UPDATE config_release SET status = 'PUBLISHED', published_at = :publishedAt WHERE release_id = :releaseId AND status = 'APPROVED'")
        .param("publishedAt", publishedAt).param("releaseId", releaseId).update();
    requireUpdated(updated);
    jdbcClient.sql("UPDATE config_release SET status = 'DISABLED' WHERE release_id <> :releaseId AND status = 'PUBLISHED'")
        .param("releaseId", releaseId).update();
    audit(authentication.getName(), "PUBLISH", releaseId, request.reason(), Map.of("status", "PUBLISHED"));
    return find(releaseId);
  }

  private void transition(String releaseId, String from, String to, String approver) {
    var sql = approver == null
        ? "UPDATE config_release SET status = :to WHERE release_id = :releaseId AND status = :from"
        : "UPDATE config_release SET status = :to, approved_by = :approver WHERE release_id = :releaseId AND status = :from";
    var statement = jdbcClient.sql(sql).param("to", to).param("releaseId", releaseId).param("from", from);
    if (approver != null) statement = statement.param("approver", approver);
    requireUpdated(statement.update());
  }

  private void requireUpdated(int updated) {
    if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "配置版本状态不允许执行该操作");
  }

  private ReleaseResponse find(String releaseId) {
    return jdbcClient.sql("SELECT release_id, version_no, status, created_by, approved_by, published_at, created_at FROM config_release WHERE release_id = :releaseId")
        .param("releaseId", releaseId).query(ReleaseResponse.class).single();
  }

  private void audit(String operator, String action, String releaseId, String reason, Object after) {
    jdbcClient.sql("INSERT INTO operation_audit (audit_id, operator_id, action, resource_type, resource_id, reason, after_summary, created_at) VALUES (:auditId, :operator, :action, 'CONFIG_RELEASE', :resourceId, :reason, :after, :createdAt)")
        .param("auditId", UUID.randomUUID().toString()).param("operator", operator).param("action", action)
        .param("resourceId", releaseId).param("reason", reason).param("after", json(after))
        .param("createdAt", Timestamp.from(Instant.now())).update();
  }

  private String json(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (JsonProcessingException exception) { throw new IllegalArgumentException("配置内容不是合法 JSON", exception); }
  }

  public record CreateReleaseRequest(@NotNull Map<String, Object> configuration, @NotBlank String reason) {}
  public record ReasonRequest(@NotBlank String reason) {}
  public record ReleaseResponse(String releaseId, long versionNo, String status, String createdBy, String approvedBy, Instant publishedAt, Instant createdAt) {}
}
