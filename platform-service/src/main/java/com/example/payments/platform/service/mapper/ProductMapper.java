package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.ProductModel;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {
  long countAll();
  List<ProductModel> selectPage(@Param("limit") int limit, @Param("offset") int offset);
  ProductModel selectByCode(@Param("productCode") String productCode);
  boolean existsByCode(@Param("productCode") String productCode);
  int insertProduct(@Param("productCode") String productCode, @Param("name") String name, @Param("now") Instant now);
  int updateProduct(@Param("productCode") String productCode, @Param("name") String name, @Param("now") Instant now);
  int updateStatus(@Param("productCode") String productCode, @Param("status") String status, @Param("now") Instant now);
}
