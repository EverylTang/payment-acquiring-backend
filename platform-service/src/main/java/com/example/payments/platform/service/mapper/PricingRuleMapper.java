package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.model.PricingRuleFull;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PricingRuleMapper {
    List<PricingRuleFull> selectByPage(@Param("offset") int offset, @Param("limit") int limit);

    int countAll();

    void insert(PricingRuleFull rule);

    void updateStatus(@Param("ruleId") String ruleId, @Param("status") String status);
}
