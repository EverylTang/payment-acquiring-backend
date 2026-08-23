package com.example.payments.platform.service.service;

import com.example.payments.platform.service.mapper.ProductCapabilityMapper;
import com.example.payments.platform.service.model.ProductCapabilityModel;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCapabilityService {
  private final ProductCapabilityMapper mapper;
  private final ProductService products;
  private final OperationAuditService audit;

  public Page list(String productCode,int page,int pageSize) {
    int safePage=Math.max(page,1),safeSize=Math.min(Math.max(pageSize,1),100);
    return new Page(mapper.selectPageByProduct(productCode,safeSize,(safePage-1)*safeSize),safePage,safeSize,mapper.countByProduct(productCode));
  }
  public ProductCapabilityModel detail(String id) {
    var value=mapper.selectById(id); if(value==null) throw new IllegalArgumentException("产品能力不存在: "+id); return value;
  }
  @Transactional public ProductCapabilityModel create(String productCode,Command command,String operator,Object payload) {
    if(!products.exists(productCode)) throw new IllegalArgumentException("产品不存在: "+productCode);
    validate(command); String id=UUID.randomUUID().toString();
    mapper.insertCapability(id,productCode,command.country(),command.currency(),command.paymentMethod(),command.minAmount(),command.maxAmount(),command.supportsRefund());
    audit.record(operator,"CREATE","PRODUCT_CAPABILITY",id,payload); return detail(id);
  }
  @Transactional public ProductCapabilityModel update(String productCode,String id,Command command,String operator,Object payload) {
    validate(command); int rows=mapper.updateCapability(id,productCode,command.country(),command.currency(),command.paymentMethod(),command.minAmount(),command.maxAmount(),command.supportsRefund());
    requireUpdated(rows,id); audit.record(operator,"UPDATE","PRODUCT_CAPABILITY",id,payload); return detail(id);
  }
  @Transactional public ProductCapabilityModel changeStatus(String productCode,String id,String status,String operator,Object payload) {
    requireUpdated(mapper.updateStatus(id,productCode,status),id); audit.record(operator,"CHANGE_STATUS","PRODUCT_CAPABILITY",id,payload); return detail(id);
  }
  private void validate(Command value) { if(value.maxAmount().compareTo(value.minAmount())<0) throw new IllegalArgumentException("最大金额不能小于最小金额"); }
  private void requireUpdated(int rows,String id) { if(rows==0) throw new IllegalArgumentException("产品能力不存在: "+id); }
  public record Command(String country,String currency,String paymentMethod,BigDecimal minAmount,BigDecimal maxAmount,boolean supportsRefund) {}
  public record Page(java.util.List<ProductCapabilityModel> items,int page,int pageSize,long total) {}
}
