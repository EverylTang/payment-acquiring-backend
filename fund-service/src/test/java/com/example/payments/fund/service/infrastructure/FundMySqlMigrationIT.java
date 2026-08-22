package com.example.payments.fund.service.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_TESTCONTAINERS", matches = "true")
class FundMySqlMigrationIT {
  @Container
  static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

  @Test
  void appliesLedgerReconciliationAndRefundMigrations() {
    Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
        .locations("classpath:db/migration").baselineOnMigrate(true).load().migrate();
    var tables = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
        .load().info().all();
    assertThat(tables).anyMatch(info -> "6".equals(info.getVersion().getVersion()));
  }
}
