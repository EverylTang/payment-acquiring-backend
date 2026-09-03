package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantProfileModel;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantProfileMapper {
  MerchantProfileModel selectByMerchantId(@Param("merchantId") String merchantId);

  int upsert(
      @Param("merchantId") String merchantId,
      @Param("legalName") String legalName,
      @Param("registeredCountry") String registeredCountry,
      @Param("industry") String industry,
      @Param("riskLevel") String riskLevel,
      @Param("taxIdentifier") String taxIdentifier,
      @Param("now") Instant now);
}
