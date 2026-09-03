package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantCallbackConfigFullModel;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCallbackConfigFullMapper {
  MerchantCallbackConfigFullModel selectByMerchantId(@Param("merchantId") String merchantId);

  int upsert(
      @Param("merchantId") String merchantId,
      @Param("callbackUrl") String callbackUrl,
      @Param("eventTypes") String eventTypes,
      @Param("status") String status,
      @Param("now") Instant now);
}
