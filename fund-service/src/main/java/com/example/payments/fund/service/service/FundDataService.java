package com.example.payments.fund.service.service;

import lombok.RequiredArgsConstructor;

import com.example.payments.fund.service.mapper.MybatisPlusClient;
import org.springframework.stereotype.Service;

/** Service boundary for reconciliation persistence. */
@Service
@RequiredArgsConstructor
public class FundDataService {
  private final MybatisPlusClient mapper;

  public MybatisPlusClient.Statement sql(String sql) {
    return mapper.sql(sql);
  }
}
