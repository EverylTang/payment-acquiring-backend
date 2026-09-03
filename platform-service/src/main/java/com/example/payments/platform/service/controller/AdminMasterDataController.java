package com.example.payments.platform.service.controller;

import com.example.payments.platform.service.service.MasterDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/v1/master-data")
@RequiredArgsConstructor
public class AdminMasterDataController {
  private final MasterDataService service;

  @GetMapping("/countries")
  @PreAuthorize("hasAuthority('master-data:list')")
  public AdminPageResponse<MasterDataService.Country> countries(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "100") int pageSize,
      @RequestParam(required = false) String status) {
    var result = service.countries(page, pageSize, status);
    return new AdminPageResponse<>(
        result.items(), result.page(), result.pageSize(), result.total());
  }

  @GetMapping("/currencies")
  @PreAuthorize("hasAuthority('master-data:list')")
  public AdminPageResponse<MasterDataService.Currency> currencies(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "100") int pageSize,
      @RequestParam(required = false) String status) {
    var result = service.currencies(page, pageSize, status);
    return new AdminPageResponse<>(
        result.items(), result.page(), result.pageSize(), result.total());
  }

  @GetMapping("/country-currencies")
  @PreAuthorize("hasAuthority('master-data:list')")
  public AdminPageResponse<MasterDataService.CountryCurrency> countryCurrencies(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "100") int pageSize,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String countryCode) {
    var result = service.countryCurrencies(page, pageSize, status, countryCode);
    return new AdminPageResponse<>(
        result.items(), result.page(), result.pageSize(), result.total());
  }

  @GetMapping("/countries/active")
  @PreAuthorize("hasAuthority('master-data:list')")
  public java.util.List<MasterDataService.Country> activeCountries() {
    return service.activeCountries();
  }

  @GetMapping("/currencies/active")
  @PreAuthorize("hasAuthority('master-data:list')")
  public java.util.List<MasterDataService.Currency> activeCurrencies(
      @RequestParam(required = false) String countryCode) {
    return service.activeCurrencies(countryCode);
  }

  @PostMapping("/country-currencies")
  @PreAuthorize("hasAuthority('master-data:create')")
  public MasterDataService.CountryCurrency createCountryCurrency(
      @Valid @RequestBody CountryCurrencyRequest request, Authentication auth) {
    return service.createCountryCurrency(
        new MasterDataService.CountryCurrencyRequest(request.countryCode(), request.currencyCode()),
        auth.getName());
  }

  @PatchMapping("/country-currencies/{countryCode}/{currencyCode}/status")
  @PreAuthorize("hasAuthority('master-data:status')")
  public void countryCurrencyStatus(
      @PathVariable String countryCode,
      @PathVariable String currencyCode,
      @Valid @RequestBody StatusRequest request,
      Authentication auth) {
    service.changeCountryCurrencyStatus(countryCode, currencyCode, request.status(), auth.getName());
  }

  @PostMapping("/countries")
  @PreAuthorize("hasAuthority('master-data:create')")
  public MasterDataService.Country createCountry(
      @Valid @RequestBody CountryRequest request, Authentication auth) {
    return service.createCountry(
        new MasterDataService.CountryRequest(
            request.code(), request.name(), request.region()),
        auth.getName());
  }

  @PutMapping("/countries/{code}")
  @PreAuthorize("hasAuthority('master-data:update')")
  public MasterDataService.Country updateCountry(
      @PathVariable String code, @Valid @RequestBody CountryRequest request, Authentication auth) {
    return service.updateCountry(
        code,
        new MasterDataService.CountryRequest(
            code, request.name(), request.region()),
        auth.getName());
  }

  @PatchMapping("/countries/{code}/status")
  @PreAuthorize("hasAuthority('master-data:status')")
  public void countryStatus(
      @PathVariable String code, @Valid @RequestBody StatusRequest request, Authentication auth) {
    service.changeCountryStatus(code, request.status(), auth.getName());
  }

  @PostMapping("/currencies")
  @PreAuthorize("hasAuthority('master-data:create')")
  public MasterDataService.Currency createCurrency(
      @Valid @RequestBody CurrencyRequest request, Authentication auth) {
    return service.createCurrency(
        new MasterDataService.CurrencyRequest(
            request.code(), request.name(), request.symbol(), request.decimalPlaces()),
        auth.getName());
  }

  @PostMapping("/countries/{countryCode}/currencies")
  @PreAuthorize("hasAuthority('master-data:create')")
  public MasterDataService.CountryCurrency createCurrencyForCountry(
      @PathVariable String countryCode,
      @Valid @RequestBody CurrencyRequest request,
      Authentication auth) {
    return service.createCurrencyForCountry(
        countryCode,
        new MasterDataService.CurrencyRequest(
            request.code(), request.name(), request.symbol(), request.decimalPlaces()),
        auth.getName());
  }

  @PutMapping("/currencies/{code}")
  @PreAuthorize("hasAuthority('master-data:update')")
  public MasterDataService.Currency updateCurrency(
      @PathVariable String code, @Valid @RequestBody CurrencyRequest request, Authentication auth) {
    return service.updateCurrency(
        code,
        new MasterDataService.CurrencyRequest(
            code, request.name(), request.symbol(), request.decimalPlaces()),
        auth.getName());
  }

  @PatchMapping("/currencies/{code}/status")
  @PreAuthorize("hasAuthority('master-data:status')")
  public void currencyStatus(
      @PathVariable String code, @Valid @RequestBody StatusRequest request, Authentication auth) {
    service.changeCurrencyStatus(code, request.status(), auth.getName());
  }

  public record CountryRequest(
      @NotBlank @Pattern(regexp = "[A-Z]{2}") String code,
      @NotBlank String name,
      String region) {}

  public record CurrencyRequest(
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String code,
      @NotBlank String name,
      String symbol,
      @Min(0) @Max(6) int decimalPlaces) {}

  public record CountryCurrencyRequest(
      @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode) {}

  public record StatusRequest(@Pattern(regexp = "ACTIVE|DISABLED") String status) {}
}
