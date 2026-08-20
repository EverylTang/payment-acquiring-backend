package com.example.payments.fund.service.interfaces.rest;

import com.example.payments.fund.service.infrastructure.persistence.LedgerEntryEntity;
import com.example.payments.fund.service.infrastructure.persistence.LedgerEntryMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/ledger")
public class LedgerController {
  private final LedgerEntryMapper mapper;

  public LedgerController(LedgerEntryMapper mapper) {
    this.mapper = mapper;
  }

  @PostMapping("/payment-success")
  public Map<String, Object> postPaymentSuccess(@Valid @RequestBody LedgerEntryRequest request) {
    try {
      LedgerEntryEntity entry = new LedgerEntryEntity();
      entry.setEntryId("entry-" + request.idempotencyKey());
      entry.setAccountId(request.merchantId());
      entry.setOrderId(request.orderId());
      entry.setEntryType("PAYMENT_SUCCESS");
      entry.setDebitCredit("CREDIT");
      entry.setAmount(request.amount());
      entry.setCurrency(request.currency());
      entry.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
      entry.setIdempotencyKey(request.idempotencyKey());
      entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
      mapper.insert(entry);
      return response(request, false);
    } catch (DuplicateKeyException duplicate) {
      return response(request, true);
    }
  }

  private Map<String, Object> response(LedgerEntryRequest request, boolean duplicate) {
    return Map.of("accepted", true, "duplicate", duplicate, "idempotencyKey", request.idempotencyKey(), "entryType", "PAYMENT_SUCCESS");
  }

  public record LedgerEntryRequest(@NotBlank String idempotencyKey, @NotBlank String orderId,
      @NotBlank String merchantId, @NotBlank String currency,
      @NotNull @DecimalMin("0.01") BigDecimal amount) {}
}
