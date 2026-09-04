package com.example.payments.platform.service.mapper;

import com.example.payments.platform.service.service.MasterDataService.Country;
import com.example.payments.platform.service.service.MasterDataService.CountryCurrency;
import com.example.payments.platform.service.service.MasterDataService.Currency;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MasterDataMapper {
  long countCountries(@Param("status") String status);

  List<Country> selectCountries(
      @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

  long countCurrencies(@Param("status") String status);

  List<Currency> selectCurrencies(
      @Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

  long countCountryCurrencies(
      @Param("status") String status, @Param("countryCode") String countryCode);

  List<CountryCurrency> selectCountryCurrencies(
      @Param("status") String status,
      @Param("countryCode") String countryCode,
      @Param("limit") int limit,
      @Param("offset") int offset);

  List<Country> selectActiveCountries();

  List<Currency> selectActiveCurrencies(@Param("countryCode") String countryCode);

  int insertCountryCurrency(
      @Param("countryCode") String countryCode,
      @Param("currencyCode") String currencyCode,
      @Param("now") Instant now);

  int insertCountry(
      @Param("code") String code,
      @Param("name") String name,
      @Param("region") String region,
      @Param("now") Instant now);

  int updateCountry(
      @Param("code") String code,
      @Param("name") String name,
      @Param("region") String region,
      @Param("now") Instant now);

  int insertCurrency(
      @Param("code") String code,
      @Param("name") String name,
      @Param("symbol") String symbol,
      @Param("places") int places,
      @Param("now") Instant now);

  int updateCurrency(
      @Param("code") String code,
      @Param("name") String name,
      @Param("symbol") String symbol,
      @Param("places") int places,
      @Param("now") Instant now);

  int updateCountryStatus(
      @Param("code") String code, @Param("status") String status, @Param("now") Instant now);

  int updateCurrencyStatus(
      @Param("code") String code, @Param("status") String status, @Param("now") Instant now);

  int updateCountryCurrencyStatus(
      @Param("countryCode") String countryCode,
      @Param("currencyCode") String currencyCode,
      @Param("status") String status,
      @Param("now") Instant now);

  long countActiveCountry(@Param("code") String code);

  long countActiveCurrency(@Param("code") String code);

  long countActiveCountryCurrency(
      @Param("country") String country, @Param("currency") String currency);

  Country selectCountry(@Param("code") String code);

  Currency selectCurrency(@Param("code") String code);

  CountryCurrency selectCountryCurrency(
      @Param("countryCode") String countryCode, @Param("currencyCode") String currencyCode);
}
