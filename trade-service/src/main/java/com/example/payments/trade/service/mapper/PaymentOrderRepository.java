package com.example.payments.trade.service.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentOrderRepository {
  private final PaymentOrderMapper mapper;

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
    entity.setPayerPayableAmount(order.payerPayableAmount());
    entity.setNetAmount(order.netAmount());
    entity.setFeeBearer(order.feeBearer());
    entity.setStatus(order.status().name());
    entity.setIdempotencyKey(order.idempotencyKey());
    entity.setRouteSnapshotJson(order.routeSnapshot());
    entity.setPricingSnapshotJson(order.pricingSnapshot());
    entity.setExpireAt(toLocal(order.expireAt()));
    entity.setCreatedAt(toLocal(order.createdAt()));
    entity.setPaymentToken(order.paymentToken());
    entity.setNotifyUrl(order.notifyUrl());
    entity.setReturnUrl(order.returnUrl());
    entity.setCustomerReference(order.customerReference());
    entity.setDescription(order.description());
    entity.setCallbackStatus(order.callbackStatus());
    entity.setCallbackEventId(order.callbackEventId());
    entity.setCallbackAttemptCount(order.callbackAttemptCount());
    entity.setCallbackLastNotifiedAt(toLocal(order.callbackLastNotifiedAt()));
    entity.setCallbackLastError(order.callbackLastError());
    entity.setVersion(0L);
    mapper.insert(entity);
    return order;
  }

  public Optional<PaymentOrder> findById(String orderId) {
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<PaymentOrderEntity>()
                    .eq(PaymentOrderEntity::getOrderId, orderId)
                    .last("LIMIT 1")))
        .map(this::toDomain);
  }

  public Optional<PaymentOrder> findByMerchantOrder(String merchantId, String merchantOrderNo) {
    return Optional.ofNullable(mapper.findByMerchantOrder(merchantId, merchantOrderNo))
        .map(this::toDomain);
  }

  public Optional<PaymentOrder> findByIdempotency(String merchantId, String key) {
    return Optional.ofNullable(mapper.findByIdempotency(merchantId, key)).map(this::toDomain);
  }

  public boolean updateStatus(
      String orderId, OrderStatus expected, OrderStatus next, Instant paidAt) {
    return mapper.updateStatus(orderId, expected, next, paidAt == null ? null : toLocal(paidAt))
        == 1;
  }

  public boolean updateCallbackState(
      String orderId,
      String callbackStatus,
      String callbackEventId,
      int callbackAttemptCount,
      Instant callbackLastNotifiedAt,
      String callbackLastError) {
    return mapper.updateCallbackState(
            orderId,
            callbackStatus,
            callbackEventId,
            callbackAttemptCount,
            callbackLastNotifiedAt == null ? null : toLocal(callbackLastNotifiedAt),
            callbackLastError)
        == 1;
  }

  public List<PaymentOrder> search(
      String merchantId, String status, String currency, int page, int pageSize) {
    var wrapper =
        new LambdaQueryWrapper<PaymentOrderEntity>()
            .eq(
                merchantId != null && !merchantId.isBlank(),
                PaymentOrderEntity::getMerchantId,
                merchantId)
            .eq(status != null && !status.isBlank(), PaymentOrderEntity::getStatus, status)
            .eq(currency != null && !currency.isBlank(), PaymentOrderEntity::getCurrency, currency)
            .orderByDesc(PaymentOrderEntity::getCreatedAt)
            .last("LIMIT " + pageSize + " OFFSET " + ((page - 1) * pageSize));
    return mapper.selectList(wrapper).stream().map(this::toDomain).toList();
  }

  public long count(String merchantId, String status, String currency) {
    var wrapper =
        new LambdaQueryWrapper<PaymentOrderEntity>()
            .eq(
                merchantId != null && !merchantId.isBlank(),
                PaymentOrderEntity::getMerchantId,
                merchantId)
            .eq(status != null && !status.isBlank(), PaymentOrderEntity::getStatus, status)
            .eq(currency != null && !currency.isBlank(), PaymentOrderEntity::getCurrency, currency);
    return mapper.selectCount(wrapper);
  }

  public OrderStatistics statistics() {
    var aggregate = mapper.aggregateStatistics();
    return new OrderStatistics(
        ((Number) aggregate.get("total")).longValue(),
        ((Number) aggregate.get("successful")).longValue(),
        (BigDecimal) aggregate.get("volume"),
        ((Number) aggregate.get("merchants")).longValue());
  }

  private PaymentOrder toDomain(PaymentOrderEntity e) {
    return new PaymentOrder(
        e.getOrderId(),
        e.getMerchantId(),
        e.getMerchantOrderNo(),
        e.getProductCode(),
        e.getPaymentMethod(),
        e.getCountry(),
        e.getCurrency(),
        e.getAmount(),
        e.getFeeAmount(),
        e.getPayerPayableAmount(),
        e.getNetAmount(),
        e.getFeeBearer(),
        OrderStatus.valueOf(e.getStatus()),
        e.getIdempotencyKey(),
        e.getRouteSnapshotJson(),
        e.getPricingSnapshotJson(),
        e.getExpireAt().toInstant(ZoneOffset.UTC),
        e.getCreatedAt().toInstant(ZoneOffset.UTC),
        e.getPaidAt() == null ? null : e.getPaidAt().toInstant(ZoneOffset.UTC),
        e.getPaymentToken(),
        e.getNotifyUrl(),
        e.getReturnUrl(),
        e.getCustomerReference(),
        e.getDescription(),
        e.getCallbackStatus(),
        e.getCallbackEventId(),
        e.getCallbackAttemptCount(),
        e.getCallbackLastNotifiedAt() == null
            ? null
            : e.getCallbackLastNotifiedAt().toInstant(ZoneOffset.UTC),
        e.getCallbackLastError());
  }

  private static LocalDateTime toLocal(Instant value) {
    if (value == null) return null;
    return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  public record OrderStatistics(long total, long successful, BigDecimal volume, long merchants) {}
}
