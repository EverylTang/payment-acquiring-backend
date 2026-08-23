package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.OperationAuditModel;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationAuditQueryMapper {
  long count(@Param("resourceType") String resourceType, @Param("operatorId") String operatorId);

  List<OperationAuditModel> select(
      @Param("resourceType") String resourceType,
      @Param("operatorId") String operatorId,
      @Param("limit") int limit,
      @Param("offset") int offset);
}
