package com.example.payments.platform.service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class MasterDataMapperXmlTest {
  @Test
  void mapsCurrencyDecimalPlacesToTheRecordPrimitiveConstructorArgument() throws Exception {
    var configuration = new Configuration();
    try (var reader = Resources.getResourceAsReader("mapper/MasterDataMapper.xml")) {
      new XMLMapperBuilder(
              reader,
              configuration,
              "mapper/MasterDataMapper.xml",
              configuration.getSqlFragments())
          .parse();
    }

    var resultMap =
        configuration.getResultMap("com.example.payments.platform.service.mapper.MasterDataMapper.currency");
    var constructorTypes =
        resultMap.getConstructorResultMappings().stream().map(mapping -> mapping.getJavaType()).toList();

    assertEquals(
        List.of(String.class, String.class, String.class, int.class, String.class), constructorTypes);
  }
}
