package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantContactModel;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantContactMapper {
  MerchantContactModel selectByMerchantId(@Param("merchantId") String merchantId);

  int upsert(
      @Param("merchantId") String merchantId,
      @Param("contactName") String contactName,
      @Param("contactEmail") String contactEmail,
      @Param("contactPhone") String contactPhone,
      @Param("now") Instant now);
}
