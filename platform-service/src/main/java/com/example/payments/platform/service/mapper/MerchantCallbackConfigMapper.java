package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantCallbackConfigModel;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantCallbackConfigMapper {
  MerchantCallbackConfigModel selectByMerchantId(@Param("merchantId") String merchantId);

  int upsert(
      @Param("merchantId") String merchantId,
      @Param("successUrl") String successUrl,
      @Param("failUrl") String failUrl,
      @Param("notifyUrl") String notifyUrl,
      @Param("now") Instant now);
}
