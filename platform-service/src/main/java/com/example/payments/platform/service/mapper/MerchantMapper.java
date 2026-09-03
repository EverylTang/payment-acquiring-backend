package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.MerchantModel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantMapper {
  long countVisible(
      @Param("username") String username,
      @Param("allScope") boolean allScope,
      @Param("merchantName") String merchantName,
      @Param("merchantId") String merchantId,
      @Param("status") String status,
      @Param("createdFrom") LocalDate createdFrom,
      @Param("createdTo") LocalDate createdTo);

  List<MerchantModel> selectVisible(
      @Param("username") String username,
      @Param("allScope") boolean allScope,
      @Param("merchantName") String merchantName,
      @Param("merchantId") String merchantId,
      @Param("status") String status,
      @Param("createdFrom") LocalDate createdFrom,
      @Param("createdTo") LocalDate createdTo,
      @Param("limit") int limit,
      @Param("offset") int offset);

  MerchantModel selectVisibleById(
      @Param("merchantId") String merchantId,
      @Param("username") String username,
      @Param("allScope") boolean allScope);

  int insertMerchant(
      @Param("merchantId") String merchantId,
      @Param("name") String name,
      @Param("now") Instant now);

  int insertMerchantWithGeneratedId(@Param("name") String name, @Param("now") Instant now);

  long selectLastInsertId();

  int updateMerchantId(@Param("id") long id, @Param("merchantId") String merchantId);

  int updateMerchant(
      @Param("merchantId") String merchantId,
      @Param("name") String name,
      @Param("now") Instant now);

  int updateStatus(
      @Param("merchantId") String merchantId,
      @Param("status") String status,
      @Param("now") Instant now);
}
