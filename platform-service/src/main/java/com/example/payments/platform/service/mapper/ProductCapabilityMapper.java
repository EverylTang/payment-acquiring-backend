package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.ProductCapabilityModel;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductCapabilityMapper {
  long countByProduct(@Param("productCode") String productCode);

  List<ProductCapabilityModel> selectPageByProduct(
      @Param("productCode") String productCode,
      @Param("limit") int limit,
      @Param("offset") int offset);

  ProductCapabilityModel selectById(@Param("capabilityId") String capabilityId);

  int insertCapability(
      @Param("id") String id,
      @Param("productCode") String productCode,
      @Param("customerPaymentMethod") String customerPaymentMethod,
      @Param("channelPaymentMethod") String channelPaymentMethod,
      @Param("minAmount") BigDecimal minAmount,
      @Param("maxAmount") BigDecimal maxAmount,
      @Param("supportsRefund") boolean supportsRefund);

  int updateCapability(
      @Param("id") String id,
      @Param("productCode") String productCode,
      @Param("customerPaymentMethod") String customerPaymentMethod,
      @Param("channelPaymentMethod") String channelPaymentMethod,
      @Param("minAmount") BigDecimal minAmount,
      @Param("maxAmount") BigDecimal maxAmount,
      @Param("supportsRefund") boolean supportsRefund);

  int updateStatus(
      @Param("id") String id,
      @Param("productCode") String productCode,
      @Param("status") String status);

  int deleteCapability(@Param("id") String id, @Param("productCode") String productCode);
}
