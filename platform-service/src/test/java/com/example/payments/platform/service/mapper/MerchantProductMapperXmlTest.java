package com.example.payments.platform.service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MerchantProductMapperXmlTest {
  @Test
  void mapsTheAutoIncrementIdAsThePublicAppId() throws Exception {
    var configuration = new Configuration();
    try (var reader = Resources.getResourceAsReader("mapper/MerchantProductMapper.xml")) {
      new XMLMapperBuilder(
              reader,
              configuration,
              "mapper/MerchantProductMapper.xml",
              configuration.getSqlFragments())
          .parse();
    }

    var resultMap =
        configuration.getResultMap(
            "com.example.payments.platform.service.mapper.MerchantProductMapper.merchantProduct");
    var constructorTypes =
        resultMap.getConstructorResultMappings().stream().map(mapping -> mapping.getJavaType()).toList();

    assertEquals(String.class, constructorTypes.get(0));
    assertEquals(
        List.of(
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            java.time.Instant.class,
            java.time.Instant.class,
            String.class),
        constructorTypes.subList(1, constructorTypes.size()));
  }
}
