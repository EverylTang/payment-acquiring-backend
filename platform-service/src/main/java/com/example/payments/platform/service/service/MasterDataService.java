package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.MybatisPlusClient;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MasterDataService {
  private final MybatisPlusClient client;
  private final OperationAuditService audit;

  public Page<Country> countries(int page, int pageSize, String status) {
    return page(
        "country_master",
        "country_code AS code, country_name AS name, region, status",
        Country.class,
        page,
        pageSize,
        status);
  }

  public Page<Currency> currencies(int page, int pageSize, String status) {
    return page(
        "currency_master",
        "currency_code AS code, currency_name AS name, symbol, decimal_places, status",
        Currency.class,
        page,
        pageSize,
        status);
  }

  public Page<CountryCurrency> countryCurrencies(
      int page, int pageSize, String status, String countryCode) {
    int current = Math.max(page, 1), size = Math.min(Math.max(pageSize, 1), 100);
    StringBuilder where = new StringBuilder();
    if (status != null && !status.isBlank()) where.append("cc.status=:status");
    if (countryCode != null && !countryCode.isBlank()) {
      if (!where.isEmpty()) where.append(" AND ");
      where.append("cc.country_code=:countryCode");
    }
    String condition = where.isEmpty() ? "" : " WHERE " + where;
    var count =
        client
            .sql("SELECT COUNT(*) FROM country_currency_master cc" + condition)
            .param("status", status)
            .param("countryCode", countryCode)
            .query(Long.class)
            .single();
    var items =
        client
            .sql(
                "SELECT cc.country_code AS countryCode,c.country_name AS countryName,cc.currency_code AS currencyCode,cu.currency_name AS currencyName,cc.status "
                    + "FROM country_currency_master cc "
                    + "JOIN country_master c ON c.country_code=cc.country_code "
                    + "JOIN currency_master cu ON cu.currency_code=cc.currency_code"
                    + condition
                    + " ORDER BY cc.status,cc.country_code,cc.currency_code LIMIT :limit OFFSET :offset")
            .param("status", status)
            .param("countryCode", countryCode)
            .param("limit", size)
            .param("offset", (current - 1) * size)
            .query(CountryCurrency.class)
            .list();
    return new Page<>(items, current, size, count);
  }

  public java.util.List<Country> activeCountries() {
    return client
        .sql(
            "SELECT country_code AS code, country_name AS name, region, status FROM country_master WHERE status='ACTIVE' ORDER BY country_name")
        .query(Country.class)
        .list();
  }

  public java.util.List<Currency> activeCurrencies(String countryCode) {
    if (countryCode != null && !countryCode.isBlank()) {
      return client
          .sql(
              "SELECT cu.currency_code AS code,cu.currency_name AS name,cu.symbol,cu.decimal_places,cu.status "
                  + "FROM country_currency_master cc "
                  + "JOIN currency_master cu ON cu.currency_code=cc.currency_code "
                  + "JOIN country_master c ON c.country_code=cc.country_code "
                  + "WHERE cc.country_code=:countryCode AND cc.status='ACTIVE' AND c.status='ACTIVE' AND cu.status='ACTIVE' "
                  + "ORDER BY cu.currency_code")
          .param("countryCode", countryCode)
          .query(Currency.class)
          .list();
    }
    return client
        .sql(
            "SELECT currency_code AS code, currency_name AS name, symbol, decimal_places, status FROM currency_master WHERE status='ACTIVE' ORDER BY currency_code")
        .query(Currency.class)
        .list();
  }

  @Transactional
  public CountryCurrency createCountryCurrency(CountryCurrencyRequest value, String operator) {
    requireCountryAndCurrencyActive(value.countryCode(), value.currencyCode());
    client
        .sql(
            "INSERT INTO country_currency_master(country_code,currency_code,status,created_at,updated_at) VALUES(:countryCode,:currencyCode,'ACTIVE',:now,:now)")
        .param("countryCode", value.countryCode())
        .param("currencyCode", value.currencyCode())
        .param("now", Instant.now())
        .update();
    audit.record(operator, "CREATE", "COUNTRY_CURRENCY", relationId(value), value);
    return countryCurrency(value.countryCode(), value.currencyCode());
  }

  @Transactional
  public Country createCountry(CountryRequest value, String operator) {
    client
        .sql(
                "INSERT INTO country_master(country_code,country_name,region,status,created_at,updated_at) VALUES(:code,:name,:region,'ACTIVE',:now,:now)")
        .param("code", value.code())
        .param("name", value.name())
        .param("region", blank(value.region()))
        .param("now", Instant.now())
        .update();
    audit.record(operator, "CREATE", "COUNTRY", value.code(), value);
    return country(value.code());
  }

  @Transactional
  public Country updateCountry(String code, CountryRequest value, String operator) {
    required(
        client
            .sql(
                "UPDATE country_master SET country_name=:name,region=:region,updated_at=:now WHERE country_code=:code")
            .param("code", code)
            .param("name", value.name())
            .param("region", blank(value.region()))
            .param("now", Instant.now())
            .update(),
        code);
    audit.record(operator, "UPDATE", "COUNTRY", code, value);
    return country(code);
  }

  @Transactional
  public Currency createCurrency(CurrencyRequest value, String operator) {
    client
        .sql(
            "INSERT INTO currency_master(currency_code,currency_name,symbol,decimal_places,status,created_at,updated_at) VALUES(:code,:name,:symbol,:places,'ACTIVE',:now,:now)")
        .param("code", value.code())
        .param("name", value.name())
        .param("symbol", blank(value.symbol()))
        .param("places", value.decimalPlaces())
        .param("now", Instant.now())
        .update();
    audit.record(operator, "CREATE", "CURRENCY", value.code(), value);
    return currency(value.code());
  }

  @Transactional
  public CountryCurrency createCurrencyForCountry(
      String countryCode, CurrencyRequest value, String operator) {
    if (count("country_master", "country_code", countryCode) == 0)
      throw new IllegalArgumentException("国家或地区不存在或已停用: " + countryCode);
    createCurrency(value, operator);
    return createCountryCurrency(new CountryCurrencyRequest(countryCode, value.code()), operator);
  }

  @Transactional
  public Currency updateCurrency(String code, CurrencyRequest value, String operator) {
    required(
        client
            .sql(
                "UPDATE currency_master SET currency_name=:name,symbol=:symbol,decimal_places=:places,updated_at=:now WHERE currency_code=:code")
            .param("code", code)
            .param("name", value.name())
            .param("symbol", blank(value.symbol()))
            .param("places", value.decimalPlaces())
            .param("now", Instant.now())
            .update(),
        code);
    audit.record(operator, "UPDATE", "CURRENCY", code, value);
    return currency(code);
  }

  @Transactional
  public void changeCountryStatus(String code, String status, String operator) {
    changeStatus("country_master", "country_code", "COUNTRY", code, status, operator);
  }

  @Transactional
  public void changeCurrencyStatus(String code, String status, String operator) {
    changeStatus("currency_master", "currency_code", "CURRENCY", code, status, operator);
  }

  @Transactional
  public void changeCountryCurrencyStatus(
      String countryCode, String currencyCode, String status, String operator) {
    required(
        client
            .sql(
                "UPDATE country_currency_master SET status=:status,updated_at=:now WHERE country_code=:countryCode AND currency_code=:currencyCode")
            .param("status", status)
            .param("now", Instant.now())
            .param("countryCode", countryCode)
            .param("currencyCode", currencyCode)
            .update(),
        countryCode + "/" + currencyCode);
    audit.record(
        operator,
        "CHANGE_STATUS",
        "COUNTRY_CURRENCY",
        countryCode + "/" + currencyCode,
        status);
  }

  public void requireActive(String country, String currency) {
    requireCountryAndCurrencyActive(country, currency);
    if (client
            .sql(
                "SELECT COUNT(*) FROM country_currency_master WHERE country_code=:country AND currency_code=:currency AND status='ACTIVE'")
            .param("country", country)
            .param("currency", currency)
            .query(Long.class)
            .single()
        == 0)
      throw new IllegalArgumentException("国家/地区与币种未建立启用关联: " + country + "/" + currency);
  }

  private <T> Page<T> page(
      String table, String columns, Class<T> type, int page, int pageSize, String status) {
    int current = Math.max(page, 1), size = Math.min(Math.max(pageSize, 1), 100);
    String where = status == null || status.isBlank() ? "" : " WHERE status=:status";
    var count =
        client
            .sql("SELECT COUNT(*) FROM " + table + where)
            .param("status", status)
            .query(Long.class)
            .single();
    var items =
        client
            .sql(
                "SELECT "
                    + columns
                    + " FROM "
                    + table
                    + where
                    + " ORDER BY status, 1 LIMIT :limit OFFSET :offset")
            .param("status", status)
            .param("limit", size)
            .param("offset", (current - 1) * size)
            .query(type)
            .list();
    return new Page<>(items, current, size, count);
  }

  private Country country(String code) {
    return client
        .sql(
            "SELECT country_code AS code,country_name AS name,region,status FROM country_master WHERE country_code=:code")
        .param("code", code)
        .query(Country.class)
        .single();
  }

  private CountryCurrency countryCurrency(String countryCode, String currencyCode) {
    return client
        .sql(
            "SELECT cc.country_code AS countryCode,c.country_name AS countryName,cc.currency_code AS currencyCode,cu.currency_name AS currencyName,cc.status "
                + "FROM country_currency_master cc "
                + "JOIN country_master c ON c.country_code=cc.country_code "
                + "JOIN currency_master cu ON cu.currency_code=cc.currency_code "
                + "WHERE cc.country_code=:countryCode AND cc.currency_code=:currencyCode")
        .param("countryCode", countryCode)
        .param("currencyCode", currencyCode)
        .query(CountryCurrency.class)
        .single();
  }

  private Currency currency(String code) {
    return client
        .sql(
            "SELECT currency_code AS code,currency_name AS name,symbol,decimal_places,status FROM currency_master WHERE currency_code=:code")
        .param("code", code)
        .query(Currency.class)
        .single();
  }

  private long count(String table, String column, String code) {
    return client
        .sql("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=:code AND status='ACTIVE'")
        .param("code", code)
        .query(Long.class)
        .single();
  }

  private void requireCountryAndCurrencyActive(String country, String currency) {
    if (count("country_master", "country_code", country) == 0)
      throw new IllegalArgumentException("国家或地区不存在或已停用: " + country);
    if (count("currency_master", "currency_code", currency) == 0)
      throw new IllegalArgumentException("币种不存在或已停用: " + currency);
  }

  private String relationId(CountryCurrencyRequest value) {
    return value.countryCode() + "/" + value.currencyCode();
  }

  private void changeStatus(
      String table, String column, String type, String code, String status, String operator) {
    required(
        client
            .sql(
                "UPDATE "
                    + table
                    + " SET status=:status,updated_at=:now WHERE "
                    + column
                    + "=:code")
            .param("status", status)
            .param("now", Instant.now())
            .param("code", code)
            .update(),
        code);
    audit.record(operator, "CHANGE_STATUS", type, code, status);
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
