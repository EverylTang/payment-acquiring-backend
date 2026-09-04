package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.service.ConfigurationAdminService.ChannelRow;
import com.example.payments.platform.service.service.ConfigurationAdminService.RiskPolicyRow;
import com.example.payments.platform.service.service.ConfigurationAdminService.RoutingRuleResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConfigurationAdminMapper {
  long countActiveMerchants();

  long countActiveChannels();

  long countPendingReleases();

  long countChannels();

  long countRoutingRules();

  long countRiskPolicies();

  List<ChannelRow> selectChannels(@Param("limit") int limit, @Param("offset") int offset);

  int insertChannel(
      @Param("id") String id,
      @Param("name") String name,
      @Param("provider") String provider,
      @Param("requestUrl") String requestUrl,
      @Param("signatureProfile") String signatureProfile,
      @Param("config") String config,
      @Param("now") Instant now);

  int insertChannelCapability(
      @Param("id") String id,
      @Param("channel") String channel,
      @Param("country") String country,
      @Param("currency") String currency,
      @Param("method") String method,
      @Param("min") BigDecimal min,
      @Param("max") BigDecimal max);

  int updateChannel(
      @Param("id") String channelId,
      @Param("name") String name,
      @Param("provider") String provider,
      @Param("requestUrl") String requestUrl,
      @Param("signatureProfile") String signatureProfile,
      @Param("config") String configuration,
      @Param("now") Instant now);

  int updateChannelStatus(
      @Param("id") String id, @Param("status") String status, @Param("now") Instant now);

  List<RoutingRuleResponse> selectRoutingRules(
      @Param("limit") int limit, @Param("offset") int offset);

  int insertRoutingRule(
      @Param("id") String id,
      @Param("version") long version,
      @Param("product") String product,
      @Param("merchant") String merchant,
      @Param("method") String method,
      @Param("country") String country,
      @Param("currency") String currency,
      @Param("channel") String channel,
      @Param("priority") int priority,
      @Param("weight") int weight);

  int updateRoutingRule(
      @Param("id") String ruleId,
      @Param("product") String productCode,
      @Param("merchant") String merchantId,
      @Param("method") String paymentMethod,
      @Param("country") String country,
      @Param("currency") String currency,
      @Param("channel") String channelId,
      @Param("priority") int priority,
      @Param("weight") int weight);

  int updateRoutingRuleStatus(
      @Param("id") String id, @Param("status") String status, @Param("now") Instant now);

  List<RiskPolicyRow> selectRiskPolicies(@Param("limit") int limit, @Param("offset") int offset);

  int insertRiskPolicy(
      @Param("id") String id,
      @Param("version") long version,
      @Param("name") String name,
      @Param("priority") int priority,
      @Param("decision") String decision,
      @Param("condition") String condition);

  int updateRiskPolicyStatus(
      @Param("id") String id, @Param("status") String status, @Param("now") Instant now);

  int updateRiskPolicy(
      @Param("id") String id,
      @Param("name") String name,
      @Param("priority") int priority,
      @Param("decision") String decision,
      @Param("condition") String condition);

  Long selectDraftVersion(@Param("releaseId") String releaseId);
}
