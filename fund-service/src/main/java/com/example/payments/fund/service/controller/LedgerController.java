package com.example.payments.fund.service.controller;

import com.example.payments.fund.service.service.LedgerEntryApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {
  private final LedgerEntryApplicationService ledgerService;
  private final InternalRequestAuthorizer internalAuthorizer;

  @PostMapping("/payment-success")
  public Map<String, Object> postPaymentSuccess(
      @Valid @RequestBody LedgerEntryRequest request,
      @RequestHeader("X-Internal-Token") String internalToken) {
    internalAuthorizer.authorize(internalToken);
    var result =
        ledgerService.recordPaymentSuccess(
            request.idempotencyKey(),
            request.orderId(),
            request.merchantId(),
            request.amount(),
            request.currency());
    return response(request, result.duplicate());
  }

  private Map<String, Object> response(LedgerEntryRequest request, boolean duplicate) {
    return Map.of(
        "accepted",
        true,
        "duplicate",
        duplicate,
        "idempotencyKey",
        request.idempotencyKey(),
        "entryType",
        "PAYMENT_SUCCESS");
  }

  public record LedgerEntryRequest(
      @NotBlank String idempotencyKey,
      @NotBlank String orderId,
      @NotBlank String merchantId,
      @NotBlank String currency,
      @NotNull @DecimalMin("0.01") BigDecimal amount) {}
}
