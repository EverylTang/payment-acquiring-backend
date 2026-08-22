package com.example.payments.trade.service;

import com.example.payments.trade.service.config.AttemptQueryProperties;
import com.example.payments.trade.service.config.OutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({OutboxProperties.class, AttemptQueryProperties.class})
public class TradeApplication {
  public static void main(String[] args) {
    SpringApplication.run(TradeApplication.class, args);
  }
}
