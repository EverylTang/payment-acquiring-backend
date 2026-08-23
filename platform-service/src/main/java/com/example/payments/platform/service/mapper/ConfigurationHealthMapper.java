package com.example.payments.platform.service.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConfigurationHealthMapper { String status(@Param("id") String id); }
