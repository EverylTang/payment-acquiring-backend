package com.example.payments.platform.service.service;

import com.example.payments.platform.service.controller.AdminPageResponse;
import com.example.payments.platform.service.mapper.RiskAdminMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RiskAdminService {
  private final RiskAdminMapper mapper;
  private final OperationAuditService audit;

  public Map<String, Long> overview() {
    return Map.of(
        "openEvents", mapper.countOpenEvents(),
        "reviewEvents", mapper.countReviewEvents(),
        "rejectedEvents", mapper.countRejectedEvents(),
        "events24h", mapper.countEventsSince(Instant.now().minus(24, ChronoUnit.HOURS)));
  }

  public AdminPageResponse<RiskEventRow> events(String status, String level, String merchantId, int page, int pageSize) {
    var q = page(page, pageSize);
    return new AdminPageResponse<>(mapper.selectEvents(empty(status), empty(level), empty(merchantId), q.size, q.offset), q.page, q.size, mapper.countEvents(empty(status), empty(level), empty(merchantId)));
  }

  public RiskEventRow event(String eventId) {
    var event = mapper.selectEvent(eventId);
    if (event == null) throw new IllegalArgumentException("风险事件不存在: " + eventId);
    return event;
  }

  @Transactional
  public void review(String eventId, ReviewRequest request, String operator) {
    event(eventId);
    var now = Instant.now();
    mapper.resolveEvent(eventId, request.decision(), request.note(), operator, now);
    mapper.upsertCase("case-" + eventId, eventId, request.decision(), request.note(), operator, now);
    audit.record(operator, "REVIEW", "RISK_EVENT", eventId, Map.of("decision", request.decision(), "note", request.note()));
  }

  public AdminPageResponse<RiskListRow> lists(String listType, int page, int pageSize) {
    var q = page(page, pageSize);
    return new AdminPageResponse<>(mapper.selectLists(empty(listType), q.size, q.offset), q.page, q.size, mapper.countLists(empty(listType)));
  }

  @Transactional
  public void recordDecision(RiskDecisionRequest request) {
    if ("PASS".equals(request.decision())) return;
    mapper.insertEvent(UUID.randomUUID().toString(), request.orderId(), request.merchantId(), request.policyId(), request.policyName(), request.decision(), "REJECT".equals(request.decision()) ? "HIGH" : "MEDIUM", request.reason(), null, null, Instant.now());
  }

  @Transactional
  public void createList(ListRequest request, String operator) {
    mapper.insertList(UUID.randomUUID().toString(), request.listType(), request.subjectType(), hash(request.subjectValue()), request.label(), request.expiresAt(), operator, Instant.now());
    audit.record(operator, "CREATE", "RISK_LIST", request.subjectType(), Map.of("listType", request.listType(), "label", request.label()));
  }

  @Transactional
  public void changeListStatus(String entryId, String status, String operator) {
    mapper.updateListStatus(entryId, status, Instant.now());
    audit.record(operator, "CHANGE_STATUS", "RISK_LIST", entryId, Map.of("status", status));
  }

  private static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.trim().getBytes(StandardCharsets.UTF_8))); }
    catch (Exception exception) { throw new IllegalStateException("名单标识无法加密", exception); }
  }
  private static String empty(String value) { return value == null || value.isBlank() ? null : value; }
  private static Page page(int page, int size) {
    if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("分页参数无效");
    return new Page(page, size, (page - 1) * size);
  }
  private record Page(int page, int size, int offset) {}
  public record RiskEventRow(String eventId, String orderId, String merchantId, String policyId, String policyName, String decision, String riskLevel, String status, String reason, String subjectType, String subjectMasked, String reviewer, String reviewDecision, String reviewNote, Instant createdAt, Instant resolvedAt) {}
  public record RiskListRow(String entryId, String listType, String subjectType, String subjectHash, String label, Instant expiresAt, String status, String createdBy, Instant createdAt) {}
  public record ReviewRequest(@jakarta.validation.constraints.Pattern(regexp = "PASS|REJECT") String decision, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 512) String note) {}
  public record ListRequest(@jakarta.validation.constraints.Pattern(regexp = "BLACK|WHITE|GREY") String listType, @jakarta.validation.constraints.Pattern(regexp = "MERCHANT|EMAIL|PHONE|IP|DEVICE|CARD") String subjectType, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 256) String subjectValue, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 128) String label, Instant expiresAt) {}
  public record RiskDecisionRequest(@jakarta.validation.constraints.NotBlank String orderId, @jakarta.validation.constraints.NotBlank String merchantId, String policyId, String policyName, @jakarta.validation.constraints.Pattern(regexp = "PASS|REVIEW|REJECT") String decision, @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 512) String reason) {}
}
