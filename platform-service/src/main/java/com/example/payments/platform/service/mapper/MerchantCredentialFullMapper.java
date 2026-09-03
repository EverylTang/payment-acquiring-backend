package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantCredentialFullModel;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCredentialFullMapper {
  List<MerchantCredentialFullModel> selectByMerchantId(@Param("merchantId") String merchantId);

  int revokeActiveByType(
      @Param("merchantId") String merchantId,
      @Param("credentialType") String credentialType,
      @Param("now") Instant now);

  int insert(
      @Param("credentialId") String credentialId,
      @Param("merchantId") String merchantId,
      @Param("credentialType") String credentialType,
      @Param("secretHash") String secretHash,
      @Param("secretHint") String secretHint,
      @Param("now") Instant now);

  int revokeById(
      @Param("credentialId") String credentialId,
      @Param("merchantId") String merchantId,
      @Param("now") Instant now);
}
