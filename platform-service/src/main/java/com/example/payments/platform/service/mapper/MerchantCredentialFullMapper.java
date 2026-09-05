package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantCredentialFullModel;
import com.example.payments.platform.service.model.MerchantApiCredentialModel;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCredentialFullMapper {
  List<MerchantCredentialFullModel> selectByMerchantId(@Param("merchantId") String merchantId);

  MerchantApiCredentialModel selectActiveApiCredential(
      @Param("credentialId") String credentialId, @Param("now") Instant now);

  MerchantApiCredentialModel selectActiveCredentialByMerchantAndType(
      @Param("merchantId") String merchantId,
      @Param("credentialType") String credentialType,
      @Param("now") Instant now);

  int claimNonce(
      @Param("merchantId") String merchantId,
      @Param("credentialId") String credentialId,
      @Param("nonce") String nonce,
      @Param("expiresAt") Instant expiresAt,
      @Param("now") Instant now);

  int revokeActiveByType(
      @Param("merchantId") String merchantId,
      @Param("credentialType") String credentialType,
      @Param("now") Instant now);

  int insert(
      @Param("credentialId") String credentialId,
      @Param("merchantId") String merchantId,
      @Param("credentialType") String credentialType,
      @Param("secretHash") String secretHash,
      @Param("secretCiphertext") String secretCiphertext,
      @Param("secretHint") String secretHint,
      @Param("expiresAt") Instant expiresAt,
      @Param("ipAllowlist") String ipAllowlist,
      @Param("now") Instant now);

  int revokeById(
      @Param("credentialId") String credentialId,
      @Param("merchantId") String merchantId,
      @Param("now") Instant now);
}
