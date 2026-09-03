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
      @Param("businessType") String businessType,
      @Param("registeredCountry") String registeredCountry,
      @Param("industry") String industry,
      @Param("businessUrl") String businessUrl,
      @Param("productDescription") String productDescription,
      @Param("statementDescriptor") String statementDescriptor,
      @Param("supportEmail") String supportEmail,
      @Param("supportPhone") String supportPhone,
      @Param("supportUrl") String supportUrl,
      @Param("addressLine1") String addressLine1,
      @Param("addressLine2") String addressLine2,
      @Param("addressCity") String addressCity,
      @Param("addressState") String addressState,
      @Param("addressPostalCode") String addressPostalCode,
      @Param("riskLevel") String riskLevel,
      @Param("taxIdentifier") String taxIdentifier,
      @Param("now") Instant now);
}
