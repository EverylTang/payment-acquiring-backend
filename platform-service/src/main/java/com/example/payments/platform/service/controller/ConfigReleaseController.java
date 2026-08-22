package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.ConfigurationSnapshotService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import com.example.payments.platform.service.mapper.MybatisPlusClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/v1/config-releases")
public class ConfigReleaseController {
  private final MybatisPlusClient mybatisClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationSnapshotService snapshotService;

  public ConfigReleaseController(
      MybatisPlusClient mybatisClient,
      ObjectMapper objectMapper,
      ConfigurationSnapshotService snapshotService) {
    this.mybatisClient = mybatisClient;
    this.objectMapper = objectMapper;
    this.snapshotService = snapshotService;
  }

  @GetMapping
  public AdminPageResponse<ReleaseResponse> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var total = mybatisClient.sql("SELECT COUNT(*) FROM config_release").query(Long.class).single();
    var items =
        mybatisClient
            .sql(
                "SELECT release_id, version_no, status, created_by, approved_by, published_at,"
                    + " created_at FROM config_release ORDER BY version_no DESC LIMIT :limit OFFSET"
                    + " :offset")
            .param("limit", size)
            .param("offset", (currentPage - 1) * size)
            .query(ReleaseResponse.class)
            .list();
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public ReleaseResponse create(
      @Valid @RequestBody CreateReleaseRequest request, Authentication authentication) {
    var version =
        mybatisClient
            .sql("SELECT COALESCE(MAX(version_no), 0) + 1 FROM config_release FOR UPDATE")
            .query(Long.class)
            .single();
    var releaseId = "release-" + UUID.randomUUID();
    mybatisClient
        .sql(
            "INSERT INTO config_release (release_id, version_no, status, config_json, created_by,"
                + " created_at) VALUES (:releaseId, :version, 'DRAFT', :config, :createdBy,"
                + " :createdAt)")
        .param("releaseId", releaseId)
        .param("version", version)
        .param("config", json(request.configuration()))
        .param("createdBy", authentication.getName())
        .param("createdAt", Instant.now())
        .update();
    audit(authentication.getName(), "CREATE", releaseId, request.reason(), request.configuration());
    return find(releaseId);
  }

  @PostMapping("/{releaseId}/submit")
  @PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
  @Transactional
  public ReleaseResponse submit(
      @PathVariable String releaseId,
      @Valid @RequestBody ReasonRequest request,
      Authentication authentication) {
    var release = find(releaseId);
    var errors = snapshotService.validate(release.versionNo());
    if (!errors.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, String.join("；", errors));
    }
    transition(releaseId, "DRAFT", "IN_REVIEW", null);
    audit(
        authentication.getName(),
        "SUBMIT",
        releaseId,
        request.reason(),
        Map.of("status", "IN_REVIEW"));
    return find(releaseId);
  }

  @PostMapping("/{releaseId}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ReleaseResponse approve(
      @PathVariable String releaseId,
      @RequestBody ReasonRequest request,
      Authentication authentication) {
    transition(releaseId, "IN_REVIEW", "APPROVED", authentication.getName());
    audit(
        authentication.getName(),
        "APPROVE",
        releaseId,
        request.reason(),
        Map.of("status", "APPROVED"));
    return find(releaseId);
  }

  @PostMapping("/{releaseId}/publish")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ReleaseResponse publish(
      @PathVariable String releaseId,
      @RequestBody ReasonRequest request,
      Authentication authentication) {
    var publishedAt = Instant.now();
    var updated =
        mybatisClient
            .sql(
                "UPDATE config_release SET status = 'PUBLISHED', published_at = :publishedAt WHERE"
                    + " release_id = :releaseId AND status = 'APPROVED'")
            .param("publishedAt", publishedAt)
            .param("releaseId", releaseId)
            .update();
    requireUpdated(updated);
    mybatisClient
        .sql(
            "UPDATE config_release SET status = 'DISABLED' WHERE release_id <> :releaseId AND"
                + " status = 'PUBLISHED'")
        .param("releaseId", releaseId)
        .update();
    audit(
        authentication.getName(),
        "PUBLISH",
        releaseId,
        request.reason(),
        Map.of("status", "PUBLISHED"));
    return find(releaseId);
  }

  @GetMapping("/{releaseId}/diff")
  public Map<String, Object> diff(@PathVariable String releaseId) {
    var release = rawConfig(releaseId);
    var previous =
        mybatisClient
            .sql(
                "SELECT config_json FROM config_release WHERE version_no < :version ORDER BY"
                    + " version_no DESC LIMIT 1")
            .param("version", release.version())
            .query(String.class)
            .optional()
            .orElse("{}");
    var currentMap = readObject(release.config());
    var previousMap = readObject(previous);
    var changed = new java.util.LinkedHashMap<String, Map<String, Object>>();
    var keys = new java.util.TreeSet<String>();
    keys.addAll(previousMap.keySet());
    keys.addAll(currentMap.keySet());
    keys.forEach(
        key -> {
          if (!java.util.Objects.equals(previousMap.get(key), currentMap.get(key))) {
            var change = new java.util.LinkedHashMap<String, Object>();
            change.put("before", previousMap.get(key));
            change.put("after", currentMap.get(key));
            changed.put(key, change);
          }
        });
    return Map.of("releaseId", releaseId, "versionNo", release.version(), "changes", changed);
  }

  @PostMapping("/{releaseId}/rollback")
  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ReleaseResponse rollback(
      @PathVariable String releaseId,
      @Valid @RequestBody ReasonRequest request,
      Authentication authentication) {
    var source = rawConfig(releaseId);
    var version =
        mybatisClient
            .sql("SELECT COALESCE(MAX(version_no), 0) + 1 FROM config_release FOR UPDATE")
            .query(Long.class)
            .single();
    var newId = "release-rollback-" + UUID.randomUUID();
    mybatisClient
        .sql(
            "INSERT INTO config_release (release_id, version_no, status, config_json, created_by,"
                + " created_at) VALUES (:id, :version, 'DRAFT', :config, :createdBy, :now)")
        .param("id", newId)
        .param("version", version)
        .param("config", source.config())
        .param("createdBy", authentication.getName())
        .param("now", Instant.now())
        .update();
    audit(
        authentication.getName(),
        "ROLLBACK",
        newId,
        request.reason(),
        Map.of("sourceReleaseId", releaseId));
    return find(newId);
  }

  private void transition(String releaseId, String from, String to, String approver) {
    var sql =
        approver == null
            ? "UPDATE config_release SET status = :to WHERE release_id = :releaseId AND status ="
                  + " :from"
            : "UPDATE config_release SET status = :to, approved_by = :approver WHERE release_id ="
                  + " :releaseId AND status = :from";
    var statement =
        mybatisClient.sql(sql).param("to", to).param("releaseId", releaseId).param("from", from);
    if (approver != null) statement = statement.param("approver", approver);
    requireUpdated(statement.update());
  }

  private void requireUpdated(int updated) {
    if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "配置版本状态不允许执行该操作");
  }

  private ReleaseResponse find(String releaseId) {
    return mybatisClient
        .sql(
            "SELECT release_id, version_no, status, created_by, approved_by, published_at,"
                + " created_at FROM config_release WHERE release_id = :releaseId")
        .param("releaseId", releaseId)
        .query(ReleaseResponse.class)
        .single();
  }

  private RawConfig rawConfig(String releaseId) {
    return mybatisClient
        .sql(
            "SELECT version_no, CAST(config_json AS CHAR) config_json FROM config_release WHERE"
                + " release_id = :releaseId")
        .param("releaseId", releaseId)
        .query(RawConfig.class)
        .single();
  }

  private Map<String, Object> readObject(String value) {
    try {
      return objectMapper.readValue(value, Map.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("配置 JSON 无法解析", exception);
    }
  }

  private void audit(
      String operator, String action, String releaseId, String reason, Object after) {
    mybatisClient
        .sql(
            "INSERT INTO operation_audit (audit_id, operator_id, action, resource_type,"
                + " resource_id, reason, after_summary, created_at) VALUES (:auditId, :operator,"
                + " :action, 'CONFIG_RELEASE', :resourceId, :reason, :after, :createdAt)")
        .param("auditId", UUID.randomUUID().toString())
        .param("operator", operator)
        .param("action", action)
        .param("resourceId", releaseId)
        .param("reason", reason)
        .param("after", json(after))
        .param("createdAt", Instant.now())
        .update();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("配置内容不是合法 JSON", exception);
    }
  }

  public record CreateReleaseRequest(
      @NotNull Map<String, Object> configuration, @NotBlank String reason) {}

  public record ReasonRequest(@NotBlank String reason) {}

  public record ReleaseResponse(
      String releaseId,
      long versionNo,
      String status,
      String createdBy,
      String approvedBy,
      Instant publishedAt,
      Instant createdAt) {}

  public record RawConfig(long version, String config) {}
}
