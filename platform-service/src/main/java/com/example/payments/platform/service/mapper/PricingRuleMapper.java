package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.PricingRuleFull;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PricingRuleMapper {
  List<PricingRuleFull> selectByPage(@Param("offset") int offset, @Param("limit") int limit);

  int countAll();

  PricingRuleFull selectByRuleId(@Param("ruleId") String ruleId);

  boolean isDraftVersion(@Param("releaseVersion") long releaseVersion);

  void insert(PricingRuleFull rule);

  void update(PricingRuleFull rule);

  void updateStatus(@Param("ruleId") String ruleId, @Param("status") String status);

  void deleteByRuleId(@Param("ruleId") String ruleId);
}
