package com.example.payments.platform.service.service;

import lombok.RequiredArgsConstructor;

import com.example.payments.platform.service.mapper.MybatisPlusClient;
import org.springframework.stereotype.Service;

/** Service boundary for platform persistence; controllers must not access mappers directly. */
@Service
@RequiredArgsConstructor
public class PlatformDataService {
  private final MybatisPlusClient mapper;

  public MybatisPlusClient.StatementSpec sql(String sql) {
    return mapper.sql(sql);
  }
}
