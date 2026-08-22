package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.MybatisPlusClient;
import org.springframework.stereotype.Service;

/** Service boundary for platform persistence; controllers must not access mappers directly. */
@Service
public class PlatformDataService {
  private final MybatisPlusClient mapper;

  public PlatformDataService(MybatisPlusClient mapper) {
    this.mapper = mapper;
  }

  public MybatisPlusClient.StatementSpec sql(String sql) {
    return mapper.sql(sql);
  }
}
