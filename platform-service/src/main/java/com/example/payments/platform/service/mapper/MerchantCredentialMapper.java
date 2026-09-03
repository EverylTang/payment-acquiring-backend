package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantCredentialModel;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCredentialMapper {
  MerchantCredentialModel selectByMerchantId(@Param("merchantId") String merchantId);

  int upsert(
      @Param("merchantId") String merchantId,
      @Param("apiKey") String apiKey,
      @Param("apiSecretHash") String apiSecretHash,
      @Param("now") Instant now);
}
