package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.service.RiskAdminService.RiskEventRow;
import com.example.payments.platform.service.service.RiskAdminService.RiskListRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RiskAdminMapper {
  long countEvents(
      @Param("status") String status,
      @Param("level") String level,
      @Param("merchantId") String merchantId);

  List<RiskEventRow> selectEvents(
      @Param("status") String status,
      @Param("level") String level,
      @Param("merchantId") String merchantId,
      @Param("limit") int limit,
      @Param("offset") int offset);

  RiskEventRow selectEvent(@Param("eventId") String eventId);

  long countOpenEvents();

  long countReviewEvents();

  long countRejectedEvents();

  long countEventsSince(@Param("since") Instant since);

  int resolveEvent(
      @Param("eventId") String eventId,
      @Param("decision") String decision,
      @Param("note") String note,
      @Param("operator") String operator,
      @Param("now") Instant now);

  int upsertCase(
      @Param("caseId") String caseId,
      @Param("eventId") String eventId,
      @Param("decision") String decision,
      @Param("note") String note,
      @Param("operator") String operator,
      @Param("now") Instant now);

  long countLists(@Param("listType") String listType);

  List<RiskListRow> selectLists(
      @Param("listType") String listType, @Param("limit") int limit, @Param("offset") int offset);

  int insertList(
      @Param("entryId") String entryId,
      @Param("listType") String listType,
      @Param("subjectType") String subjectType,
      @Param("subjectHash") String subjectHash,
      @Param("label") String label,
      @Param("expiresAt") Instant expiresAt,
      @Param("operator") String operator,
      @Param("now") Instant now);

  int updateListStatus(
      @Param("entryId") String entryId, @Param("status") String status, @Param("now") Instant now);

  int insertEvent(
      @Param("eventId") String eventId,
      @Param("orderId") String orderId,
      @Param("merchantId") String merchantId,
      @Param("policyId") String policyId,
      @Param("policyName") String policyName,
      @Param("decision") String decision,
      @Param("riskLevel") String riskLevel,
      @Param("reason") String reason,
      @Param("subjectType") String subjectType,
      @Param("subjectMasked") String subjectMasked,
      @Param("now") Instant now);
}
