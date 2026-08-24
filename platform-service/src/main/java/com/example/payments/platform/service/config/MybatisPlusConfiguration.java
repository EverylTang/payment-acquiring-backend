package com.example.payments.platform.service.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.injector.SqlRunnerInjector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfiguration {
  @Bean
  ConfigurationCustomizer sqlRunnerConfigurationCustomizer() {
    return configuration -> new SqlRunnerInjector().inject(configuration);
  }
}
