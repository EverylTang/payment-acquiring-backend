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

  public Page list(int page, int pageSize) {
    int safePage=Math.max(page,1), safeSize=Math.min(Math.max(pageSize,1),100);
    return new Page(mapper.selectPage(safeSize,(safePage-1)*safeSize),safePage,safeSize,mapper.countAll());
  }
  public ProductModel detail(String code) {
    ProductModel value=mapper.selectByCode(code);
    if(value==null) throw new IllegalArgumentException("产品不存在: "+code);
    return value;
  }
  public boolean exists(String code) { return mapper.existsByCode(code); }
  @Transactional public ProductModel create(String code,String name,String operator,Object payload) { mapper.insertProduct(code,name,Instant.now()); audit.record(operator,"CREATE","PRODUCT",code,payload); return detail(code); }
  @Transactional public ProductModel update(String code,String name,String operator,Object payload) { requireUpdated(mapper.updateProduct(code,name,Instant.now()),code); audit.record(operator,"UPDATE","PRODUCT",code,payload); return detail(code); }
  @Transactional public ProductModel changeStatus(String code,String status,String operator,Object payload) { requireUpdated(mapper.updateStatus(code,status,Instant.now()),code); audit.record(operator,"CHANGE_STATUS","PRODUCT",code,payload); return detail(code); }
  private void requireUpdated(int rows,String code) { if(rows==0) throw new IllegalArgumentException("产品不存在: "+code); }
  public record Page(java.util.List<ProductModel> items,int page,int pageSize,long total) {}
}
