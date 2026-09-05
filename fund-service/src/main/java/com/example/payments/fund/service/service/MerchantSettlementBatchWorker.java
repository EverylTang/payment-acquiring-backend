package com.example.payments.fund.service.service;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantSettlementBatchWorker {
  private final MerchantSettlementService settlementService;

  @Value("${fund.settlement.zone:UTC}")
  private String zone;

  @Scheduled(cron = "${fund.settlement.cron:0 0 1 * * *}", zone = "${fund.settlement.zone:UTC}")
  public void generateDueSettlements() {
    LocalDate settlementDate = LocalDate.now(ZoneId.of(zone));
    try {
      settlementService.generateSettlementBatch(settlementDate);
    } catch (RuntimeException exception) {
      log.error("Scheduled settlement batch failed for {}", settlementDate, exception);
    }
  }
}
