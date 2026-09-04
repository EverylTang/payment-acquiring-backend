package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.service.ConfigurationSnapshotService.ChannelCandidate;
import com.example.payments.platform.service.service.ConfigurationSnapshotService.ChannelRuntime;
import com.example.payments.platform.service.service.ConfigurationSnapshotService.CredentialBinding;
import com.example.payments.platform.service.service.ConfigurationSnapshotService.Pricing;
import com.example.payments.platform.service.service.ConfigurationSnapshotService.ProductCapability;
import com.example.payments.platform.service.service.ConfigurationSnapshotService.RiskPolicy;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConfigurationSnapshotMapper {
  Long selectLatestPublishedVersion();

  long countActiveMerchant(@Param("merchantId") String merchantId);

  long countActiveProduct(@Param("productCode") String productCode);

  long countActiveMerchantProduct(
      @Param("merchantId") String merchantId, @Param("productCode") String productCode);

  ProductCapability selectProductCapability(
      @Param("productCode") String productCode,
      @Param("paymentMethod") String paymentMethod,
      @Param("amount") BigDecimal amount);

  List<ChannelCandidate> selectChannelCandidates(
      @Param("version") long version,
      @Param("productCode") String productCode,
      @Param("merchantId") String merchantId,
      @Param("paymentMethod") String paymentMethod,
      @Param("country") String country,
      @Param("currency") String currency,
      @Param("amount") BigDecimal amount);

  ChannelRuntime selectChannelRuntime(@Param("channelId") String channelId);

  List<CredentialBinding> selectActiveChannelCredentialBindings(
      @Param("channelId") String channelId);

  Pricing selectPricing(
      @Param("version") long version,
      @Param("productCode") String productCode,
      @Param("merchantId") String merchantId,
      @Param("channelId") String channelId,
      @Param("currency") String currency,
      @Param("amount") BigDecimal amount);

  RiskPolicy selectRiskPolicy(
      @Param("version") long version,
      @Param("productCode") String productCode,
      @Param("currency") String currency);

  long countActiveRoutingRules(@Param("version") long version);

  long countActivePricingRules(@Param("version") long version);

  long countActiveRiskPolicies(@Param("version") long version);

  long countInactiveRoutingChannels(@Param("version") long version);

  long countInvalidPricingRules(@Param("version") long version);

  long countConflictingRoutingRules(@Param("version") long version);
}
