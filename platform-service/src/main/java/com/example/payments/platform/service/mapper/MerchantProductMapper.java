package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.service.MerchantProductAdminService.MerchantProductResponse;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MerchantProductMapper {
  long countPage(
      @Param("merchantName") String merchantName,
      @Param("merchantId") String merchantId,
      @Param("productName") String productName,
      @Param("productCode") String productCode,
      @Param("status") String status,
      @Param("hasAllScope") boolean hasAllScope,
      @Param("username") String username);

  List<MerchantProductResponse> selectPage(
      @Param("merchantName") String merchantName,
      @Param("merchantId") String merchantId,
      @Param("productName") String productName,
      @Param("productCode") String productCode,
      @Param("status") String status,
      @Param("hasAllScope") boolean hasAllScope,
      @Param("username") String username,
      @Param("limit") int limit,
      @Param("offset") int offset);

  MerchantProductResponse selectByBindingId(
      @Param("bindingId") String bindingId,
      @Param("hasAllScope") boolean hasAllScope,
      @Param("username") String username);

  long countByMerchantAndProduct(
      @Param("merchantId") String merchantId, @Param("productCode") String productCode);

  long countOtherByMerchantAndProduct(
      @Param("merchantId") String merchantId,
      @Param("productCode") String productCode,
      @Param("bindingId") String bindingId);

  long countActiveMerchant(@Param("merchantId") String merchantId);

  long countActiveProduct(@Param("productCode") String productCode);

  int insert(
      @Param("bindingId") String bindingId,
      @Param("merchantId") String merchantId,
      @Param("productCode") String productCode,
      @Param("now") Instant now);

  int update(
      @Param("bindingId") String bindingId,
      @Param("merchantId") String merchantId,
      @Param("productCode") String productCode,
      @Param("now") Instant now);

  int updateStatus(
      @Param("bindingId") String bindingId,
      @Param("status") String status,
      @Param("now") Instant now);
}
