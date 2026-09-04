package com.example.payments.trade.service.service;

import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
  private final PaymentOrderRepository repository;
  private final PlatformChannelConfigurationClient channelConfiguration;
  private final ObjectMapper objectMapper;

  @Autowired
  public OrderService(
      PaymentOrderRepository repository,
      PlatformChannelConfigurationClient channelConfiguration,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.channelConfiguration = channelConfiguration;
    this.objectMapper = objectMapper;
  }

  OrderService(PaymentOrderRepository repository) {
    this(repository, null, null);
  }

  @Transactional
  public PaymentOrder create(CreateOrderCommand command) {
    var existing =
        repository
            .findByIdempotency(command.merchantId(), command.idempotencyKey())
            .or(
                () ->
                    repository.findByMerchantOrder(
                        command.merchantId(), command.merchantOrderNo()));
    if (existing.isPresent()) {
      return existing.get();
    }
    PaymentOrder order =
        PaymentOrder.create(
            command.merchantId(),
            command.merchantOrderNo(),
            command.productCode(),
            command.paymentMethod(),
            command.country(),
            command.currency(),
            command.amount(),
            command.idempotencyKey(),
            command.expireAt());
    if (channelConfiguration != null) {
      order = applyPricing(order, channelConfiguration.resolveConfiguration(order));
    }
    try {
      return repository.insert(order);
    } catch (DuplicateKeyException duplicate) {
      return repository
          .findByIdempotency(command.merchantId(), command.idempotencyKey())
          .or(() -> repository.findByMerchantOrder(command.merchantId(), command.merchantOrderNo()))
          .orElseThrow(() -> duplicate);
    }
  }

  public PaymentOrder get(String orderId) {
    return repository
        .findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
  }

  public Map<String, Object> list(
      String merchantId, String status, String currency, int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new IllegalArgumentException("invalid pagination");
    var items =
        repository.search(merchantId, status, currency, page, pageSize).stream()
            .map(com.example.payments.trade.service.controller.OrderDtos.OrderResponse::from)
            .toList();
    return Map.of(
        "items",
        items,
        "page",
        page,
        "pageSize",
        pageSize,
        "total",
        repository.count(merchantId, status, currency));
  }

  public Map<String, Object> statistics() {
    var statistics = repository.statistics();
    BigDecimal successRate =
        statistics.total() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(statistics.successful() * 100.0 / statistics.total())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    return Map.of(
        "totalOrders",
        statistics.total(),
        "successfulOrders",
        statistics.successful(),
        "paymentSuccessRate",
        successRate,
        "paymentVolume",
        statistics.volume(),
        "activeMerchants",
        statistics.merchants());
  }

  @Transactional
  public PaymentOrder markPaying(String orderId) {
    return transition(orderId, OrderStatus.PAYING);
  }

  @Transactional
  public PaymentOrder callback(String orderId, OrderStatus status) {
    if (status == OrderStatus.PAYING || status == OrderStatus.CREATED) {
      transition(orderId, status);
      return get(orderId);
    }
    if (status != OrderStatus.SUCCESS
        && status != OrderStatus.FAILED
        && status != OrderStatus.UNKNOWN
        && status != OrderStatus.CANCELED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "callback status is not allowed");
    }
    PaymentOrder current = get(orderId);
    if (current.status().canTransitionTo(status)) {
      repository.updateStatus(
          orderId, current.status(), status, status == OrderStatus.SUCCESS ? Instant.now() : null);
    }
    return get(orderId);
  }

  @Transactional
  public PaymentOrder cancel(String orderId) {
    PaymentOrder current = get(orderId);
    if (current.status().isTerminal()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "terminal order cannot be canceled");
    }
    if (current.expireAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "expired order cannot be canceled");
    }
    repository.updateStatus(orderId, current.status(), OrderStatus.CANCELED, null);
    return get(orderId);
  }

  private PaymentOrder transition(String orderId, OrderStatus next) {
    PaymentOrder current = get(orderId);
    if (current.status().canTransitionTo(next)) {
      repository.updateStatus(orderId, current.status(), next, null);
    }
    return get(orderId);
  }

  private PaymentOrder applyPricing(
      PaymentOrder order, PlatformChannelConfigurationClient.ResolvedPaymentConfiguration config) {
    var fee = calculateFee(order.amount(), config).add(config.extraFee());
    if (config.minFee() != null && fee.compareTo(config.minFee()) < 0) fee = config.minFee();
    if (config.maxFee() != null && fee.compareTo(config.maxFee()) > 0) fee = config.maxFee();
    fee = fee.setScale(2, RoundingMode.HALF_UP);
    var net = order.amount();
    if ("MERCHANT_BEAR".equals(config.feeMode()) || "INCLUSIVE".equals(config.feeMode())) {
      if (fee.compareTo(order.amount()) > 0) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "内含手续费不能超过交易金额");
      }
      net = order.amount().subtract(fee);
    }
    try {
      var routeSnapshot =
          objectMapper.writeValueAsString(
              Map.of(
                  "channelId", config.runtime().channelId(),
                  "provider", config.runtime().provider(),
                  "configVersion", config.configVersion()));
      var pricing = new LinkedHashMap<String, Object>();
      pricing.put("ruleId", config.pricingRuleId());
      pricing.put("channelId", config.runtime().channelId());
      pricing.put("feeRate", config.feeRate());
      pricing.put("fixedFee", config.fixedFee());
      pricing.put("extraFee", config.extraFee());
      pricing.put("minFee", config.minFee());
      pricing.put("maxFee", config.maxFee());
      pricing.put("feeType", config.feeType());
      pricing.put("tiers", config.tiers());
      pricing.put("mode", config.feeMode());
      pricing.put("feeAmount", fee);
      pricing.put("netAmount", net);
      pricing.put("configVersion", config.configVersion());
      var pricingSnapshot = objectMapper.writeValueAsString(pricing);
      return order.withPricing(fee, net, routeSnapshot, pricingSnapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法记录订单费率快照", exception);
    }
  }

  private BigDecimal calculateFee(
      BigDecimal amount, PlatformChannelConfigurationClient.ResolvedPaymentConfiguration config) {
    return switch (config.feeType()) {
      case "FIXED" -> config.fixedFee();
      case "PERCENTAGE" -> amount.multiply(config.feeRate());
      case "TIERED" ->
          config.tiers().stream()
              .filter(
                  tier ->
                      amount.compareTo(tier.minAmount()) >= 0
                          && amount.compareTo(tier.maxAmount()) <= 0)
              .findFirst()
              .map(tier -> amount.multiply(tier.feeRate()).add(tier.fixedFee()))
              .orElseThrow(
                  () ->
                      new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "交易金额未匹配阶梯手续费"));
      case "COMBINED" -> amount.multiply(config.feeRate()).add(config.fixedFee());
      default -> throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "手续费类型无效");
    };
  }

  public record CreateOrderCommand(
      String merchantId,
      String merchantOrderNo,
      String productCode,
      String paymentMethod,
      String country,
      String currency,
      java.math.BigDecimal amount,
      String idempotencyKey,
      Instant expireAt) {
    public CreateOrderCommand {
      if (expireAt == null) expireAt = Instant.now().plus(Duration.ofMinutes(30));
    }
  }
}
