package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.ProductMapper;
import com.example.payments.platform.service.model.ProductModel;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {
  private final ProductMapper mapper;
  private final OperationAuditService audit;
  private final MasterDataService masterData;

  public Page list(int page, int pageSize, String status, String productType) {
    int safePage = Math.max(page, 1), safeSize = Math.min(Math.max(pageSize, 1), 100);
    return new Page(
        mapper.selectPage(status, productType, safeSize, (safePage - 1) * safeSize),
        safePage,
        safeSize,
        mapper.count(status, productType));
  }

  public ProductModel detail(String code) {
    ProductModel value = mapper.selectByCode(code);
    if (value == null) throw new IllegalArgumentException("产品不存在: " + code);
    return value;
  }

  public boolean exists(String code) {
    return mapper.existsByCode(code);
  }

  @Transactional
  public ProductModel create(String code, Command command, String operator, Object payload) {
    validate(command);
    mapper.insertProduct(
        code,
        command.name(),
        command.productType(),
        command.accessMode(),
        command.defaultCountry(),
        command.defaultCurrency(),
        command.description(),
        command.statementDescriptor(),
        Instant.now());
    audit.record(operator, "CREATE", "PRODUCT", code, payload);
    return detail(code);
  }

  @Transactional
  public ProductModel update(String code, Command command, String operator, Object payload) {
    validate(command);
    requireUpdated(
        mapper.updateProduct(
            code,
            command.name(),
            command.productType(),
            command.accessMode(),
            command.defaultCountry(),
            command.defaultCurrency(),
            command.description(),
            command.statementDescriptor(),
            Instant.now()),
        code);
    audit.record(operator, "UPDATE", "PRODUCT", code, payload);
    return detail(code);
  }

  @Transactional
  public ProductModel changeStatus(String code, String status, String operator, Object payload) {
    requireUpdated(mapper.updateStatus(code, status, Instant.now()), code);
    audit.record(operator, "CHANGE_STATUS", "PRODUCT", code, payload);
    return detail(code);
  }

  private void requireUpdated(int rows, String code) {
    if (rows == 0) throw new IllegalArgumentException("产品不存在: " + code);
  }

  private void validate(Command value) {
    masterData.requireActive(value.defaultCountry(), value.defaultCurrency());
    if (value.productType().equals("PAYIN") && value.accessMode() == null) {
      throw new IllegalArgumentException("收款产品必须指定接入模式");
    }
    if (value.productType().equals("PAYOUT") && value.accessMode() != null) {
      throw new IllegalArgumentException("出款产品不应指定收款接入模式");
    }
  }

  public record Command(
      String name,
      String productType,
      String accessMode,
      String defaultCountry,
      String defaultCurrency,
      String description,
      String statementDescriptor) {}

  public record Page(java.util.List<ProductModel> items, int page, int pageSize, long total) {}
}
