package com.example.payments.trade.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.payments.trade.service.domain.RefundStatus;
import com.example.payments.trade.service.mapper.PaymentAttemptMapper;
import com.example.payments.trade.service.mapper.PaymentOutboxEventRepository;
import com.example.payments.trade.service.mapper.PaymentRefundMapper;
import com.example.payments.trade.service.mapper.RefundAttemptMapper;
import com.example.payments.trade.service.mapper.RefundCallbackRecordMapper;
import com.example.payments.trade.service.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundService {
  private final PaymentRefundMapper mapper;
  private final OrderService orderService;
  private final ChannelAdapterRegistry channelAdapters;
  private final PlatformChannelConfigurationClient channelConfiguration;
  private final PaymentOutboxEventRepository outbox;
  private final RefundCallbackRecordMapper callbackMapper;
  private final RefundAttemptMapper attemptMapper;
  private final PaymentAttemptMapper paymentAttemptMapper;
  private final MeterRegistry metrics;
  private final PaymentSuccessEventSigner eventSigner;
  private final ObjectMapper objectMapper;
  private final String workerId = UUID.randomUUID().toString();

  @Value("${trade.refund.callback-timeout-seconds:600}")
  private long refundCallbackTimeoutSeconds;

  @Transactional
  public PaymentRefundEntity create(
      String orderId, String idempotencyKey, BigDecimal amount, String reason) {
    var order = orderService.get(orderId);
    if (order.orderType() == com.example.payments.trade.service.domain.OrderType.PAYOUT) {
      throw new IllegalStateException("出款订单不支持退款，请走出款冲正流程");
    }
    if (!"SUCCESS".equals(order.status().name())) throw new IllegalStateException("只有支付成功订单允许退款");
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
    if (!supportsRefund(order)) throw new IllegalStateException("订单产品能力不支持退款");
    var runtime = channelConfiguration.resolve(order);
    if (!channelAdapters
        .required(runtime.provider(), runtime.signatureProfile())
        .supportsRefund()) {
      throw new IllegalStateException("当前支付渠道未提供退款能力");
    }
    if (amount.signum() <= 0) throw new IllegalArgumentException("退款金额必须大于 0");
    if (mapper.lockOrder(orderId) == null) throw new IllegalArgumentException("订单不存在: " + orderId);
    var refunded = mapper.refundedAmount(orderId);
    if (refunded.add(amount).compareTo(order.amount()) > 0)
      throw new IllegalStateException("退款金额超过可退余额");
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
    if (orderService.get(refund.getOrderId()).orderType()
        == com.example.payments.trade.service.domain.OrderType.PAYOUT) {
      throw new IllegalStateException("出款订单不支持退款，请走出款冲正流程");
    }
    if (RefundStatus.SUCCESS.name().equals(refund.getStatus())
        || RefundStatus.CANCELED.name().equals(refund.getStatus())) return refund;
    boolean callbackTimedOut = RefundStatus.PROCESSING.name().equals(refund.getStatus());
    var now = LocalDateTime.now(ZoneOffset.UTC);
    if (mapper.claimForExecution(refundId, workerId, now, now.plusMinutes(2)) != 1)
      return get(refundId);
    refund = get(refundId);
    try {
      if (callbackTimedOut) {
        return markCallbackTimeout(refund);
      }
      var attemptNo =
          attemptMapper
                  .selectCount(
                      new LambdaQueryWrapper<RefundAttemptEntity>()
                          .eq(RefundAttemptEntity::getRefundId, refund.getRefundId()))
                  .intValue()
              + 1;
      var paymentAttempt = successfulPaymentAttempt(refund.getOrderId());
      var runtime = channelConfiguration.resolve(paymentAttempt.getChannelId());
      var channel = channelAdapters.required(runtime.provider(), runtime.signatureProfile());
      var attempt = new RefundAttemptEntity();
      attempt.setAttemptId("refund-attempt-" + UUID.randomUUID());
      attempt.setRefundId(refund.getRefundId());
      attempt.setChannelId(runtime.channelId());
      attempt.setChannelRequestNo("refund-" + refund.getRefundId());
      attempt.setAttemptNo(attemptNo);
      attempt.setStatus("PROCESSING");
      attempt.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
      attempt.setRequestSnapshot(
          "{\"orderId\":\""
              + refund.getOrderId()
              + "\",\"channelOrderId\":\""
              + paymentAttempt.getChannelRequestNo()
              + "\"}");
      attemptMapper.insert(attempt);
      var result =
          channel.refundPayment(
              new PaymentChannelAdapter.PaymentRefundRequest(
                  refund.getRefundId(),
                  refund.getOrderId(),
                  paymentAttempt.getChannelRequestNo(),
                  refund.getAmount().toPlainString(),
                  refund.getCurrency(),
                  runtime));
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
      refund.setProcessingUntil(
          RefundStatus.PROCESSING.name().equals(refund.getStatus())
              ? LocalDateTime.now(ZoneOffset.UTC).plusSeconds(refundCallbackTimeoutSeconds)
              : null);
      refund.setNextAttemptAt(
          RefundStatus.FAILED.name().equals(refund.getStatus())
              ? LocalDateTime.now(ZoneOffset.UTC)
              : null);
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
            .and(
                w ->
                    w.and(
                            x ->
                                x.in(
                                        PaymentRefundEntity::getStatus,
                                        RefundStatus.CREATED.name(),
                                        RefundStatus.FAILED.name())
                                    .le(
                                        PaymentRefundEntity::getNextAttemptAt,
                                        LocalDateTime.now(ZoneOffset.UTC)))
                        .or(
                            x ->
                                x.eq(PaymentRefundEntity::getStatus, RefundStatus.PROCESSING.name())
                                    .and(
                                        y ->
                                            y.isNull(PaymentRefundEntity::getProcessingUntil)
                                                .or()
                                                .lt(
                                                    PaymentRefundEntity::getProcessingUntil,
                                                    LocalDateTime.now(ZoneOffset.UTC)))))
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
    var refund = get(refundId);
    var paymentAttempt = successfulPaymentAttempt(refund.getOrderId());
    var runtime = channelConfiguration.resolve(paymentAttempt.getChannelId());
    var channel = channelAdapters.required(runtime.provider(), runtime.signatureProfile());
    var verified =
        channel.verifyRefundCallback(
            new PaymentChannelAdapter.PaymentRefundCallbackRequest(
                payload, signature, callbackId, timestamp, nonce, runtime));
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
    var nextStatus = RefundStatus.valueOf(status.toUpperCase());
    refund.setStatus(nextStatus.name());
    refund.setCallbackId(callbackId);
    var now = LocalDateTime.now(ZoneOffset.UTC);
    refund.setCompletedAt(nextStatus == RefundStatus.SUCCESS ? now : null);
    refund.setNextAttemptAt(nextStatus == RefundStatus.FAILED ? now : null);
    refund.setProcessingOwner(null);
    refund.setProcessingUntil(
        nextStatus == RefundStatus.PROCESSING
            ? now.plusSeconds(refundCallbackTimeoutSeconds)
            : null);
    refund.setUpdatedAt(now);
    mapper.updateById(refund);
    record.setStatus("PROCESSED");
    record.setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
    callbackMapper.updateById(record);
    if (RefundStatus.SUCCESS.name().equals(refund.getStatus())) publishReversal(refund);
    return refund;
  }

  private PaymentAttemptEntity successfulPaymentAttempt(String orderId) {
    var attempt = paymentAttemptMapper.findLatestSuccessfulByOrderId(orderId);
    if (attempt == null) {
      throw new IllegalStateException("退款缺少成功支付渠道尝试");
    }
    return attempt;
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

  private boolean supportsRefund(com.example.payments.trade.service.domain.PaymentOrder order) {
    try {
      var pricing = objectMapper.readValue(order.pricingSnapshot(), java.util.Map.class);
      if (pricing.containsKey("supportsRefund")) {
        return Boolean.TRUE.equals(pricing.get("supportsRefund"));
      }
      // Orders created before this snapshot field use the currently published capability.
      return channelConfiguration.resolveConfiguration(order).supportsRefund();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("订单缺少可验证的退款能力快照", exception);
    }
  }

  private PaymentRefundEntity markCallbackTimeout(PaymentRefundEntity refund) {
    refund.setStatus(RefundStatus.DEAD.name());
    refund.setLastError("channel refund callback timed out; manual reconciliation required");
    refund.setNextAttemptAt(null);
    refund.setProcessingOwner(null);
    refund.setProcessingUntil(null);
    refund.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
    mapper.updateById(refund);
    metrics.counter("payment.refund.dead", "service", "trade").increment();
    return refund;
  }

  private void publishReversal(PaymentRefundEntity refund) {
    outbox.insert(
        "refund-succeeded-" + refund.getRefundId(),
        refund.getRefundId(),
        "REFUND_SUCCEEDED",
        eventSigner.signedPayload(
            java.util.Map.of(
                "schemaVersion",
                1,
                "eventType",
                "REFUND_SUCCEEDED",
                "orderType",
                "PAYIN",
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
  }
}
