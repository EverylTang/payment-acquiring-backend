package com.example.payments.trade.service.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.payments.trade.service.domain.RefundStatus;
import com.example.payments.trade.service.infrastructure.persistence.PaymentAttemptMapper;
import com.example.payments.trade.service.infrastructure.persistence.PaymentOutboxEventRepository;
import com.example.payments.trade.service.infrastructure.persistence.PaymentRefundEntity;
import com.example.payments.trade.service.infrastructure.persistence.PaymentRefundMapper;
import com.example.payments.trade.service.infrastructure.persistence.RefundAttemptEntity;
import com.example.payments.trade.service.infrastructure.persistence.RefundAttemptMapper;
import com.example.payments.trade.service.infrastructure.persistence.RefundCallbackRecordEntity;
import com.example.payments.trade.service.infrastructure.persistence.RefundCallbackRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class RefundService {
  private final PaymentRefundMapper mapper;
  private final OrderService orderService;
  private final PaymentChannelAdapter channel;
  private final PaymentOutboxEventRepository outbox;
  private final ObjectMapper objectMapper;
  private final RefundCallbackRecordMapper callbackMapper;
  private final RefundAttemptMapper attemptMapper;
  private final PaymentAttemptMapper paymentAttemptMapper;
  private final MeterRegistry metrics;
  private final String workerId = UUID.randomUUID().toString();

  public RefundService(
      PaymentRefundMapper mapper,
      OrderService orderService,
      PaymentChannelAdapter channel,
      PaymentOutboxEventRepository outbox,
      ObjectMapper objectMapper,
      RefundCallbackRecordMapper callbackMapper,
      RefundAttemptMapper attemptMapper,
      PaymentAttemptMapper paymentAttemptMapper,
      MeterRegistry metrics) {
    this.mapper = mapper;
    this.orderService = orderService;
    this.channel = channel;
    this.outbox = outbox;
    this.objectMapper = objectMapper;
    this.callbackMapper = callbackMapper;
    this.attemptMapper = attemptMapper;
    this.paymentAttemptMapper = paymentAttemptMapper;
    this.metrics = metrics;
  }

  @Transactional
  public PaymentRefundEntity create(
      String orderId, String idempotencyKey, BigDecimal amount, String reason) {
    var order = orderService.get(orderId);
    if (!"SUCCESS".equals(order.status().name())) throw new IllegalStateException("只有支付成功订单允许退款");
    if (amount.signum() <= 0) throw new IllegalArgumentException("退款金额必须大于 0");
    if (mapper.lockOrder(orderId) == null) throw new IllegalArgumentException("订单不存在: " + orderId);
    var refunded = mapper.refundedAmount(orderId);
    if (refunded.add(amount).compareTo(order.amount()) > 0)
      throw new IllegalStateException("退款金额超过可退余额");
    var existing =
        mapper.selectOne(
            new LambdaQueryWrapper<PaymentRefundEntity>()
                .eq(PaymentRefundEntity::getMerchantId, order.merchantId())
                .eq(PaymentRefundEntity::getIdempotencyKey, idempotencyKey));
    if (existing != null) {
      if (existing.getAmount().compareTo(amount) != 0
          || !java.util.Objects.equals(existing.getReason(), reason))
        throw new IllegalStateException("幂等键与原退款请求不一致");
      return existing;
    }
    var now = LocalDateTime.now(ZoneOffset.UTC);
    var entity = new PaymentRefundEntity();
    entity.setRefundId(UUID.randomUUID().toString());
    entity.setOrderId(orderId);
    entity.setMerchantId(order.merchantId());
    entity.setIdempotencyKey(idempotencyKey);
    entity.setAmount(amount);
    entity.setCurrency(order.currency());
    entity.setStatus(RefundStatus.CREATED.name());
    entity.setAttemptCount(0);
    entity.setNextAttemptAt(now);
    entity.setReason(reason);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    try {
      mapper.insert(entity);
      return entity;
    } catch (DuplicateKeyException duplicate) {
      var raced =
          mapper.selectOne(
              new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                      PaymentRefundEntity>()
                  .eq(PaymentRefundEntity::getMerchantId, order.merchantId())
                  .eq(PaymentRefundEntity::getIdempotencyKey, idempotencyKey));
      if (raced == null
          || raced.getAmount().compareTo(amount) != 0
          || !java.util.Objects.equals(raced.getReason(), reason))
        throw new IllegalStateException("幂等键与原退款请求不一致", duplicate);
      return raced;
    }
  }

  public PaymentRefundEntity get(String refundId) {
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<PaymentRefundEntity>()
                    .eq(PaymentRefundEntity::getRefundId, refundId)))
        .orElseThrow(() -> new IllegalArgumentException("退款不存在: " + refundId));
  }

  @Transactional
  public PaymentRefundEntity execute(String refundId) {
    var refund = get(refundId);
    if (RefundStatus.SUCCESS.name().equals(refund.getStatus())
        || RefundStatus.CANCELED.name().equals(refund.getStatus())) return refund;
    var now = LocalDateTime.now(ZoneOffset.UTC);
    if (mapper.claimForExecution(refundId, workerId, now, now.plusMinutes(2)) != 1) return get(refundId);
    refund = get(refundId);
    try {
      var attemptNo =
          attemptMapper
                  .selectCount(
                      new LambdaQueryWrapper<RefundAttemptEntity>()
                          .eq(RefundAttemptEntity::getRefundId, refund.getRefundId()))
                  .intValue()
              + 1;
      var channelOrder = paymentAttemptMapper.findSuccessfulChannelOrder(refund.getOrderId());
      var attempt = new RefundAttemptEntity();
      attempt.setAttemptId("refund-attempt-" + UUID.randomUUID());
      attempt.setRefundId(refund.getRefundId());
      attempt.setChannelId("simulated-channel");
      attempt.setChannelRequestNo("refund-" + refund.getRefundId());
      attempt.setAttemptNo(attemptNo);
      attempt.setStatus("PROCESSING");
      attempt.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
      attempt.setRequestSnapshot(
          "{\"orderId\":\""
              + refund.getOrderId()
              + "\",\"channelOrderId\":\""
              + (channelOrder == null ? "" : channelOrder)
              + "\"}");
      attemptMapper.insert(attempt);
      var result =
          channel.refundPayment(
              new PaymentChannelAdapter.PaymentRefundRequest(
                  refund.getRefundId(),
                  refund.getOrderId(),
                  channelOrder == null ? refund.getOrderId() : channelOrder,
                  refund.getAmount().toPlainString(),
                  refund.getCurrency()));
      attempt.setChannelRequestNo(result.channelRefundId());
      attempt.setStatus(result.status());
      attempt.setResponseSnapshot(result.responseSnapshot());
      attempt.setFailureCode(result.failureCode());
      attempt.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
      attemptMapper.updateById(attempt);
      refund.setChannelRefundId(result.channelRefundId());
      refund.setStatus(RefundStatus.valueOf(result.status()).name());
      refund.setLastError(result.failureCode());
      refund.setCompletedAt(
          RefundStatus.SUCCESS.name().equals(refund.getStatus())
              ? LocalDateTime.now(ZoneOffset.UTC)
              : null);
      refund.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
      refund.setProcessingOwner(null);
      refund.setProcessingUntil(null);
      mapper.updateById(refund);
      if (RefundStatus.SUCCESS.name().equals(refund.getStatus())) publishReversal(refund);
      return refund;
    } catch (RuntimeException ex) {
      refund.setStatus(
          refund.getAttemptCount() >= 8 ? RefundStatus.DEAD.name() : RefundStatus.FAILED.name());
      refund.setLastError(
          ex.getMessage() == null
              ? "channel refund failed"
              : ex.getMessage().substring(0, Math.min(512, ex.getMessage().length())));
      refund.setNextAttemptAt(
          refund.getStatus().equals(RefundStatus.DEAD.name())
              ? null
              : LocalDateTime.now(ZoneOffset.UTC)
                  .plusSeconds(Math.min(3600, 30L << Math.min(refund.getAttemptCount(), 6))));
      refund.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
      refund.setProcessingOwner(null);
      refund.setProcessingUntil(null);
      mapper.updateById(refund);
      if (RefundStatus.DEAD.name().equals(refund.getStatus())) {
        metrics.counter("payment.refund.dead", "service", "trade").increment();
      }
      return refund;
    }
  }

  public List<PaymentRefundEntity> due(int limit) {
    return mapper.selectList(
        new LambdaQueryWrapper<PaymentRefundEntity>()
            .and(w -> w.and(x -> x.in(PaymentRefundEntity::getStatus, RefundStatus.CREATED.name(), RefundStatus.FAILED.name()).le(PaymentRefundEntity::getNextAttemptAt, LocalDateTime.now(ZoneOffset.UTC)))
                .or(x -> x.eq(PaymentRefundEntity::getStatus, RefundStatus.PROCESSING.name()).lt(PaymentRefundEntity::getProcessingUntil, LocalDateTime.now(ZoneOffset.UTC))))
            .last("LIMIT " + Math.min(limit, 100)));
  }

  @Transactional
  public PaymentRefundEntity callback(
      String callbackId,
      String refundId,
      String status,
      String payload,
      String signature,
      long timestamp,
      String nonce) {
    var verified =
        channel.verifyRefundCallback(
            new PaymentChannelAdapter.PaymentRefundCallbackRequest(
                payload, signature, callbackId, timestamp, nonce));
    if (!refundId.equals(verified.refundId()) || !status.equalsIgnoreCase(verified.status()))
      throw new IllegalArgumentException("退款回调内容不一致");
    var hash = sha256(payload);
    var existing =
        callbackMapper.selectOne(
            new LambdaQueryWrapper<RefundCallbackRecordEntity>()
                .eq(RefundCallbackRecordEntity::getCallbackId, callbackId));
    if (existing != null) {
      if (!hash.equals(existing.getPayloadHash()) || !refundId.equals(existing.getRefundId()))
        throw new IllegalStateException("退款回调标识冲突");
      return get(refundId);
    }
    var record = new RefundCallbackRecordEntity();
    record.setCallbackId(callbackId);
    record.setRefundId(refundId);
    record.setPayloadHash(hash);
    record.setStatus("PROCESSING");
    record.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    try {
      callbackMapper.insert(record);
    } catch (DuplicateKeyException duplicate) {
      var raced =
          callbackMapper.selectOne(
              new LambdaQueryWrapper<RefundCallbackRecordEntity>()
                  .eq(RefundCallbackRecordEntity::getCallbackId, callbackId));
      if (raced == null
          || !hash.equals(raced.getPayloadHash())
          || !refundId.equals(raced.getRefundId()))
        throw new IllegalStateException("退款回调标识冲突", duplicate);
      return get(refundId);
    }
    var refund = get(refundId);
    refund.setStatus(RefundStatus.valueOf(status.toUpperCase()).name());
    refund.setCallbackId(callbackId);
    refund.setCompletedAt(
        RefundStatus.SUCCESS.name().equals(refund.getStatus())
            ? LocalDateTime.now(ZoneOffset.UTC)
            : null);
    refund.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
    mapper.updateById(refund);
    record.setStatus("PROCESSED");
    record.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
    callbackMapper.updateById(record);
    if (RefundStatus.SUCCESS.name().equals(refund.getStatus())) publishReversal(refund);
    return refund;
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private void publishReversal(PaymentRefundEntity refund) {
    try {
      outbox.insert(
          "refund-succeeded-" + refund.getRefundId(),
          refund.getRefundId(),
          "REFUND_SUCCEEDED",
          objectMapper.writeValueAsString(
              java.util.Map.of(
                  "schemaVersion",
                  1,
                  "eventId",
                  "refund-succeeded-" + refund.getRefundId(),
                  "refundId",
                  refund.getRefundId(),
                  "orderId",
                  refund.getOrderId(),
                  "merchantId",
                  refund.getMerchantId(),
                  "amount",
                  refund.getAmount(),
                  "currency",
                  refund.getCurrency())));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("refund event serialization failed", ex);
    }
  }
}
