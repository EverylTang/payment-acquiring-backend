package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantContactFullModel;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantContactFullMapper {
  List<MerchantContactFullModel> selectByMerchantId(@Param("merchantId") String merchantId);

  MerchantContactFullModel selectById(@Param("id") long id, @Param("merchantId") String merchantId);

  int insert(
      @Param("merchantId") String merchantId,
      @Param("contactType") String contactType,
      @Param("contactName") String contactName,
      @Param("email") String email,
      @Param("phone") String phone,
      @Param("notifyEnabled") boolean notifyEnabled,
      @Param("now") Instant now);

  int update(
      @Param("id") long id,
      @Param("merchantId") String merchantId,
      @Param("contactType") String contactType,
      @Param("contactName") String contactName,
      @Param("email") String email,
      @Param("phone") String phone,
      @Param("notifyEnabled") boolean notifyEnabled,
      @Param("now") Instant now);

  int deleteById(@Param("id") long id, @Param("merchantId") String merchantId);
}
