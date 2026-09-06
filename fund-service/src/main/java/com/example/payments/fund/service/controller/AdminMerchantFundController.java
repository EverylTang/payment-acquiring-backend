package com.example.payments.fund.service.controller;

import com.example.payments.fund.service.service.MerchantFundQueryService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/merchant-funds")
@RequiredArgsConstructor
public class AdminMerchantFundController {
  private final MerchantFundQueryService service;
  private final AdminRequestAuthorizer authorizer;

  @GetMapping("/accounts")
  public MerchantFundQueryService.Page<MerchantFundQueryService.AccountView> accounts(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String merchantId,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String status,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "merchant-fund:account:list");
    return service.listAccounts(page, pageSize, merchantId, currency, status);
  }

  @GetMapping("/transactions")
  public MerchantFundQueryService.Page<MerchantFundQueryService.TransactionView> transactions(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String merchantId,
      @RequestParam(required = false) String currency,
      @RequestParam(required = false) String transactionType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdTo,
      @RequestHeader("X-Gateway-Token") String token,
      @RequestHeader("X-User-Id") String operator,
      @RequestHeader("X-Permissions") String permissions) {
    authorizer.authorize(token, operator, permissions, "merchant-fund:transaction:list");
    return service.listTransactions(
        page, pageSize, merchantId, currency, transactionType, createdFrom, createdTo);
  }
}
