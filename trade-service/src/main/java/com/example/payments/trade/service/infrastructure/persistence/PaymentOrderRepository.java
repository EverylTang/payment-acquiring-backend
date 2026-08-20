package com.example.payments.trade.service.infrastructure.persistence;

import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOrderRepository {
  private final PaymentOrderMapper mapper;

  public PaymentOrderRepository(PaymentOrderMapper mapper) {
    this.mapper = mapper;
  }

  public PaymentOrder insert(PaymentOrder order) {
    PaymentOrderEntity entity = new PaymentOrderEntity();
    entity.setOrderId(order.orderId());
    entity.setMerchantId(order.merchantId());
    entity.setMerchantOrderNo(order.merchantOrderNo());
    entity.setProductCode(order.productCode());
    entity.setPaymentMethod(order.paymentMethod());
    entity.setCountry(order.country());
    entity.setCurrency(order.currency());
    entity.setAmount(order.amount());
    entity.setFeeAmount(order.feeAmount());
    entity.setNetAmount(order.netAmount());
    entity.setStatus(order.status().name());
    entity.setIdempotencyKey(order.idempotencyKey());
    entity.setRouteSnapshotJson(order.routeSnapshot());
    entity.setPricingSnapshotJson(order.pricingSnapshot());
    entity.setExpireAt(toLocal(order.expireAt()));
    entity.setCreatedAt(toLocal(order.createdAt()));
    entity.setPaymentToken(order.paymentToken());
    entity.setVersion(0L);
    mapper.insert(entity);
    return order;
  }

  public Optional<PaymentOrder> findById(String orderId) {
    return Optional.ofNullable(mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PaymentOrderEntity>()
        .eq(PaymentOrderEntity::getOrderId, orderId).last("LIMIT 1"))).map(this::toDomain);
  }

  public Optional<PaymentOrder> findByMerchantOrder(String merchantId, String merchantOrderNo) {
    return Optional.ofNullable(mapper.findByMerchantOrder(merchantId, merchantOrderNo)).map(this::toDomain);
  }

  public Optional<PaymentOrder> findByIdempotency(String merchantId, String key) {
    return Optional.ofNullable(mapper.findByIdempotency(merchantId, key)).map(this::toDomain);
  }

  public boolean updateStatus(String orderId, OrderStatus expected, OrderStatus next, Instant paidAt) {
    return mapper.updateStatus(orderId, expected, next, paidAt == null ? null : toLocal(paidAt)) == 1;
  }

  private PaymentOrder toDomain(PaymentOrderEntity e) {
    return new PaymentOrder(e.getOrderId(), e.getMerchantId(), e.getMerchantOrderNo(), e.getProductCode(),
        e.getPaymentMethod(), e.getCountry(), e.getCurrency(), e.getAmount(), e.getFeeAmount(), e.getNetAmount(),
        OrderStatus.valueOf(e.getStatus()), e.getIdempotencyKey(), e.getRouteSnapshotJson(), e.getPricingSnapshotJson(),
        e.getExpireAt().toInstant(ZoneOffset.UTC), e.getCreatedAt().toInstant(ZoneOffset.UTC),
        e.getPaidAt() == null ? null : e.getPaidAt().toInstant(ZoneOffset.UTC), e.getPaymentToken());
  }

  private static LocalDateTime toLocal(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
