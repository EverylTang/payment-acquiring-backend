package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.ConfigReleaseMapper;
import com.example.payments.platform.service.mapper.OperationAuditMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ConfigReleaseService {
  private final ConfigReleaseMapper mapper;
  private final OperationAuditMapper auditMapper;
  private final ObjectMapper objectMapper;
  private final ConfigurationSnapshotService snapshotService;

  public AdminPageResponse<ReleaseResponse> list(int page, int pageSize) {
    var currentPage = Math.max(page, 1);
    var size = Math.min(Math.max(pageSize, 1), 100);
    var total = mapper.countAll();
    var items = mapper.selectPage(size, (currentPage - 1) * size);
    return new AdminPageResponse<>(items, currentPage, size, total);
  }

  @Transactional
  public ReleaseResponse create(CreateReleaseRequest request, Authentication authentication) {
    var version = mapper.nextVersionForUpdate();
    var releaseId = "release-" + UUID.randomUUID();
    mapper.insert(
        releaseId, version, json(request.configuration()), authentication.getName(), Instant.now());
    audit(authentication.getName(), "CREATE", releaseId, request.reason(), request.configuration());
    return find(releaseId);
  }

  @Transactional
  public ReleaseResponse submit(
      String releaseId, ReasonRequest request, Authentication authentication) {
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

  @Transactional
  public ReleaseResponse approve(
      String releaseId, ReasonRequest request, Authentication authentication) {
    transition(releaseId, "IN_REVIEW", "APPROVED", authentication.getName());
    audit(
        authentication.getName(),
        "APPROVE",
        releaseId,
        request.reason(),
        Map.of("status", "APPROVED"));
    return find(releaseId);
  }

  @Transactional
  public ReleaseResponse publish(
      String releaseId, ReasonRequest request, Authentication authentication) {
    var publishedAt = Instant.now();
    var updated = mapper.publish(releaseId, publishedAt);
    requireUpdated(updated);
    mapper.disableOtherPublished(releaseId);
    audit(
        authentication.getName(),
        "PUBLISH",
        releaseId,
        request.reason(),
        Map.of("status", "PUBLISHED"));
    return find(releaseId);
  }

  public Map<String, Object> diff(String releaseId) {
    var release = rawConfig(releaseId);
    var previous =
        java.util.Optional.ofNullable(mapper.selectPreviousConfig(release.version())).orElse("{}");
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

  @Transactional
  public ReleaseResponse rollback(
      String releaseId, ReasonRequest request, Authentication authentication) {
    var source = rawConfig(releaseId);
    var version = mapper.nextVersionForUpdate();
    var newId = "release-rollback-" + UUID.randomUUID();
    mapper.insert(newId, version, source.config(), authentication.getName(), Instant.now());
    audit(
        authentication.getName(),
        "ROLLBACK",
        newId,
        request.reason(),
        Map.of("sourceReleaseId", releaseId));
    return find(newId);
  }

  private void transition(String releaseId, String from, String to, String approver) {
    requireUpdated(mapper.transition(releaseId, from, to, approver));
  }

  private void requireUpdated(int updated) {
    if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "配置版本状态不允许执行该操作");
  }

  private ReleaseResponse find(String releaseId) {
    var release = mapper.selectById(releaseId);
    if (release == null) throw new IllegalArgumentException("配置版本不存在: " + releaseId);
    return release;
  }

  private RawConfig rawConfig(String releaseId) {
    var config = mapper.selectRawById(releaseId);
    if (config == null) throw new IllegalArgumentException("配置版本不存在: " + releaseId);
    return config;
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
    auditMapper.insertAuditWithReason(
        UUID.randomUUID().toString(),
        operator,
        action,
        "CONFIG_RELEASE",
        releaseId,
        reason,
        json(after),
        Instant.now());
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
