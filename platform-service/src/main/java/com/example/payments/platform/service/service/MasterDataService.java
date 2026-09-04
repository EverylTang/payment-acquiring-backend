package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.MasterDataMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MasterDataService {
  private final MasterDataMapper mapper;
  private final OperationAuditService audit;

  public Page<Country> countries(int page, int pageSize, String status) {
    int current = Math.max(page, 1), size = Math.min(Math.max(pageSize, 1), 100);
    return new Page<>(mapper.selectCountries(status, size, (current - 1) * size), current, size, mapper.countCountries(status));
  }

  public Page<Currency> currencies(int page, int pageSize, String status) {
    int current = Math.max(page, 1), size = Math.min(Math.max(pageSize, 1), 100);
    return new Page<>(mapper.selectCurrencies(status, size, (current - 1) * size), current, size, mapper.countCurrencies(status));
  }

  public Page<CountryCurrency> countryCurrencies(
      int page, int pageSize, String status, String countryCode) {
    int current = Math.max(page, 1), size = Math.min(Math.max(pageSize, 1), 100);
    var count = mapper.countCountryCurrencies(status, countryCode);
    var items = mapper.selectCountryCurrencies(status, countryCode, size, (current - 1) * size);
    return new Page<>(items, current, size, count);
  }

  public java.util.List<Country> activeCountries() {
    return mapper.selectActiveCountries();
  }

  public java.util.List<Currency> activeCurrencies(String countryCode) {
    return mapper.selectActiveCurrencies(countryCode);
  }

  @Transactional
  public CountryCurrency createCountryCurrency(CountryCurrencyRequest value, String operator) {
    requireCountryAndCurrencyActive(value.countryCode(), value.currencyCode());
    mapper.insertCountryCurrency(value.countryCode(), value.currencyCode(), Instant.now());
    audit.record(operator, "CREATE", "COUNTRY_CURRENCY", relationId(value), value);
    return countryCurrency(value.countryCode(), value.currencyCode());
  }

  @Transactional
  public Country createCountry(CountryRequest value, String operator) {
    mapper.insertCountry(value.code(), value.name(), blank(value.region()), Instant.now());
    audit.record(operator, "CREATE", "COUNTRY", value.code(), value);
    return country(value.code());
  }

  @Transactional
  public Country updateCountry(String code, CountryRequest value, String operator) {
    required(mapper.updateCountry(code, value.name(), blank(value.region()), Instant.now()), code);
    audit.record(operator, "UPDATE", "COUNTRY", code, value);
    return country(code);
  }

  @Transactional
  public Currency createCurrency(CurrencyRequest value, String operator) {
    mapper.insertCurrency(value.code(), value.name(), blank(value.symbol()), value.decimalPlaces(), Instant.now());
    audit.record(operator, "CREATE", "CURRENCY", value.code(), value);
    return currency(value.code());
  }

  @Transactional
  public CountryCurrency createCurrencyForCountry(
      String countryCode, CurrencyRequest value, String operator) {
    if (mapper.countActiveCountry(countryCode) == 0)
      throw new IllegalArgumentException("国家或地区不存在或已停用: " + countryCode);
    createCurrency(value, operator);
    return createCountryCurrency(new CountryCurrencyRequest(countryCode, value.code()), operator);
  }

  @Transactional
  public Currency updateCurrency(String code, CurrencyRequest value, String operator) {
    required(mapper.updateCurrency(code, value.name(), blank(value.symbol()), value.decimalPlaces(), Instant.now()), code);
    audit.record(operator, "UPDATE", "CURRENCY", code, value);
    return currency(code);
  }

  @Transactional
  public void changeCountryStatus(String code, String status, String operator) {
    required(mapper.updateCountryStatus(code, status, Instant.now()), code);
    audit.record(operator, "CHANGE_STATUS", "COUNTRY", code, status);
  }

  @Transactional
  public void changeCurrencyStatus(String code, String status, String operator) {
    required(mapper.updateCurrencyStatus(code, status, Instant.now()), code);
    audit.record(operator, "CHANGE_STATUS", "CURRENCY", code, status);
  }

  @Transactional
  public void changeCountryCurrencyStatus(
      String countryCode, String currencyCode, String status, String operator) {
    required(mapper.updateCountryCurrencyStatus(countryCode, currencyCode, status, Instant.now()), countryCode + "/" + currencyCode);
    audit.record(
        operator,
        "CHANGE_STATUS",
        "COUNTRY_CURRENCY",
        countryCode + "/" + currencyCode,
        status);
  }

  public void requireActive(String country, String currency) {
    requireCountryAndCurrencyActive(country, currency);
    if (mapper.countActiveCountryCurrency(country, currency) == 0)
      throw new IllegalArgumentException("国家/地区与币种未建立启用关联: " + country + "/" + currency);
  }

  private Country country(String code) {
    return mapper.selectCountry(code);
  }

  private CountryCurrency countryCurrency(String countryCode, String currencyCode) {
    return mapper.selectCountryCurrency(countryCode, currencyCode);
  }

  private Currency currency(String code) {
    return mapper.selectCurrency(code);
  }

  private void requireCountryAndCurrencyActive(String country, String currency) {
    if (mapper.countActiveCountry(country) == 0)
      throw new IllegalArgumentException("国家或地区不存在或已停用: " + country);
    if (mapper.countActiveCurrency(currency) == 0)
      throw new IllegalArgumentException("币种不存在或已停用: " + currency);
  }

  private String relationId(CountryCurrencyRequest value) {
    return value.countryCode() + "/" + value.currencyCode();
  }

  private void required(int updated, String code) {
    if (updated == 0) throw new IllegalArgumentException("基础数据不存在: " + code);
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record Country(String code, String name, String region, String status) {}

  public record Currency(
      String code, String name, String symbol, int decimalPlaces, String status) {}

  public record CountryCurrency(
      String countryCode,
      String countryName,
      String currencyCode,
      String currencyName,
      String status) {}

  public record CountryRequest(String code, String name, String region) {}

  public record CurrencyRequest(String code, String name, String symbol, int decimalPlaces) {}

  public record CountryCurrencyRequest(String countryCode, String currencyCode) {}

  public record Page<T>(java.util.List<T> items, int page, int pageSize, long total) {}
}
