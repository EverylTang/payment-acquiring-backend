package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.RoutingRuleFull;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoutingRuleMapper {
  List<RoutingRuleFull> selectByPage(@Param("offset") int offset, @Param("limit") int limit);

  int countAll();

  RoutingRuleFull selectByRuleId(@Param("ruleId") String ruleId);

  void insert(RoutingRuleFull rule);

  void update(RoutingRuleFull rule);

  void updateStatus(@Param("ruleId") String ruleId, @Param("status") String status);

  void deleteByRuleId(@Param("ruleId") String ruleId);
}
