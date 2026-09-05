package com.example.payments.trade.service.service;

import com.example.payments.trade.service.config.OrderExpirationProperties;
import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.OrderType;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentAttemptRepository;
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
  private final OrderNumberGenerator orderNumberGenerator;
  private final MerchantCallbackUrlPolicy callbackUrlPolicy;
  private final MerchantNotificationOutboxService merchantNotificationOutboxService;
  private final OrderExpirationProperties expirationProperties;
  private final PaymentAttemptRepository attemptRepository;

  @Autowired
  public OrderService(
      PaymentOrderRepository repository,
      PlatformChannelConfigurationClient channelConfiguration,
      ObjectMapper objectMapper,
      OrderNumberGenerator orderNumberGenerator,
      MerchantCallbackUrlPolicy callbackUrlPolicy,
      MerchantNotificationOutboxService merchantNotificationOutboxService,
      OrderExpirationProperties expirationProperties,
      PaymentAttemptRepository attemptRepository) {
    this.repository = repository;
    this.channelConfiguration = channelConfiguration;
    this.objectMapper = objectMapper;
    this.orderNumberGenerator = orderNumberGenerator;
    this.callbackUrlPolicy = callbackUrlPolicy;
    this.merchantNotificationOutboxService = merchantNotificationOutboxService;
    this.expirationProperties = expirationProperties;
    this.attemptRepository = attemptRepository;
  }

  OrderService(
      PaymentOrderRepository repository,
      PlatformChannelConfigurationClient channelConfiguration,
      ObjectMapper objectMapper,
      OrderNumberGenerator orderNumberGenerator,
      MerchantCallbackUrlPolicy callbackUrlPolicy,
      MerchantNotificationOutboxService merchantNotificationOutboxService,
      OrderExpirationProperties expirationProperties) {
    this(
        repository,
        channelConfiguration,
        objectMapper,
        orderNumberGenerator,
        callbackUrlPolicy,
        merchantNotificationOutboxService,
        expirationProperties,
        null);
  }

  OrderService(PaymentOrderRepository repository) {
    this(
        repository,
        null,
        null,
        new OrderNumberGenerator(0, System::currentTimeMillis),
        new MerchantCallbackUrlPolicy(false),
        null,
        OrderExpirationProperties.defaults(),
        null);
  }

  OrderService(
      PaymentOrderRepository repository,
      PlatformChannelConfigurationClient channelConfiguration,
      ObjectMapper objectMapper) {
    this(
        repository,
        channelConfiguration,
        objectMapper,
        new OrderNumberGenerator(0, System::currentTimeMillis),
        new MerchantCallbackUrlPolicy(false),
        null,
        OrderExpirationProperties.defaults(),
        null);
  }

  @Transactional
  public PaymentOrder create(CreateOrderCommand command) {
    final var resolvedOrderType =
        channelConfiguration == null
            ? OrderType.PAYIN
            : OrderType.fromProductType(channelConfiguration.productType(command.productCode()));
    var existing =
        repository
            .findByIdempotency(
                command.merchantId(), command.idempotencyKey(), resolvedOrderType.name())
            .or(
                () ->
                    repository.findByMerchantOrder(
                        command.merchantId(), command.merchantOrderNo(), resolvedOrderType.name()));
    if (existing.isPresent()) {
      return existing.get();
    }
    Instant now = Instant.now();
    if (resolvedOrderType == OrderType.PAYOUT
        && (command.payoutDestinationRef() == null || command.payoutDestinationRef().isBlank())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "出款订单必须提供收款方引用");
    }
    PaymentOrder order = draft(command);
    PlatformChannelConfigurationClient.ResolvedPaymentConfiguration configuration = null;
    if (channelConfiguration != null) {
      configuration = channelConfiguration.resolveConfiguration(order);
      if (resolvedOrderType != OrderType.fromProductType(configuration.productType())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "产品类型与路由配置不一致");
      }
    }
    validateExpiry(
        command.expireAt(),
        now,
        configuration == null
            ? expirationProperties.maxValiditySeconds()
            : maxValiditySeconds(configuration.runtime()));
    for (int attempt = 0; attempt < 3; attempt++) {
      var candidate =
          order.withIdentity(orderNumberGenerator.next(resolvedOrderType), resolvedOrderType);
      if (configuration != null) candidate = applyPricing(candidate, configuration);
      try {
        var inserted = repository.insert(candidate);
        if (configuration != null) channelConfiguration.recordRiskDecision(inserted, configuration);
        return inserted;
      } catch (DuplicateKeyException duplicate) {
        var raced =
            repository
                .findByIdempotency(
                    command.merchantId(), command.idempotencyKey(), resolvedOrderType.name())
                .or(
                    () ->
                        repository.findByMerchantOrder(
                            command.merchantId(),
                            command.merchantOrderNo(),
                            resolvedOrderType.name()));
        if (raced.isPresent()) return raced.get();
      }
    }
    throw new IllegalStateException("平台订单号冲突，请重试");
  }

  private PaymentOrder draft(CreateOrderCommand command) {
    return PaymentOrder.create(
        "draft",
        OrderType.PAYIN,
        command.merchantId(),
        command.merchantOrderNo(),
        command.productCode(),
        command.paymentMethod(),
        command.country(),
        command.currency(),
        command.amount(),
        command.idempotencyKey(),
        merchantRequestSnapshot(command),
        command.expireAt(),
        callbackUrlPolicy.validate(command.notifyUrl(), "notifyUrl"),
        callbackUrlPolicy.validate(command.returnUrl(), "returnUrl"),
        command.customerReference(),
        command.payoutDestinationRef(),
        command.description());
  }

  public PaymentOrder get(String orderId) {
    return repository
        .findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
  }

  public Map<String, Object> list(
      String merchantId, String status, String currency, String orderType, int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new IllegalArgumentException("invalid pagination");
    var items =
        repository.search(merchantId, status, currency, orderType, page, pageSize).stream()
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
        repository.count(merchantId, status, currency, orderType));
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
    if (status == OrderStatus.SUCCESS && isExpired(current, Instant.now())) {
      expire(current, Instant.now());
      return get(orderId);
    }
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
    if (!current.expireAt().isAfter(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "expired order cannot be canceled");
    }
    if (attemptRepository != null && attemptRepository.hasOpenAttemptByOrderId(orderId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "payment attempt is still pending channel reconciliation");
    }
    repository.updateStatus(orderId, current.status(), OrderStatus.CANCELED, null);
    return get(orderId);
  }

  private PaymentOrder transition(String orderId, OrderStatus next) {
    PaymentOrder current = get(orderId);
    requireActive(current, Instant.now());
    if (current.status().canTransitionTo(next)) {
      repository.updateStatus(orderId, current.status(), next, null);
    }
    return get(orderId);
  }

  public PaymentOrder requireActive(String orderId) {
    return requireActive(get(orderId), Instant.now());
  }

  @Transactional
  public int expireDue(Instant now, int limit) {
    if (limit < 1 || limit > expirationProperties.batchSize()) {
      throw new IllegalArgumentException("invalid expiration batch size");
    }
    int expired = 0;
    for (PaymentOrder order : repository.findExpirable(now, limit)) {
      if (expire(order, now)) {
        expired++;
      }
    }
    return expired;
  }

  private PaymentOrder requireActive(PaymentOrder order, Instant now) {
    if (isExpired(order, now)) {
      expire(order, now);
      throw new ResponseStatusException(HttpStatus.CONFLICT, "order has expired");
    }
    if (order.status().isTerminal()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "terminal order cannot be processed");
    }
    return order;
  }

  private boolean expire(PaymentOrder order, Instant now) {
    if (!repository.expire(order.orderId(), order.status(), now)) return false;
    if (merchantNotificationOutboxService != null) {
      merchantNotificationOutboxService.enqueueOrderExpired(
          order.withStatus(OrderStatus.EXPIRED, null));
    }
    return true;
  }

  private static boolean isExpired(PaymentOrder order, Instant now) {
    return !order.expireAt().isAfter(now);
  }

  private void validateExpiry(Instant expireAt, Instant now, long maximumValiditySeconds) {
    if (!expireAt.isAfter(now.plusSeconds(expirationProperties.minValiditySeconds()))
        || expireAt.isAfter(now.plusSeconds(maximumValiditySeconds))) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "expireAt is outside the permitted validity window");
    }
  }

  private long maxValiditySeconds(ChannelRuntimeContext runtime) {
    String configured = runtime.setting("maxOrderValiditySeconds");
    if (configured.isBlank()) return expirationProperties.maxValiditySeconds();
    try {
      long seconds = Long.parseLong(configured);
      if (seconds < expirationProperties.minValiditySeconds() || seconds > 604_800) {
        throw new NumberFormatException();
      }
      return seconds;
    } catch (NumberFormatException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "渠道订单有效期配置无效");
    }
  }

  private String merchantRequestSnapshot(CreateOrderCommand command) {
    try {
      var fields = new LinkedHashMap<String, Object>();
      fields.put("merchantId", command.merchantId());
      fields.put("merchantOrderNo", command.merchantOrderNo());
      fields.put("productCode", command.productCode());
      fields.put("paymentMethod", command.paymentMethod());
      fields.put("country", command.country());
      fields.put("currency", command.currency());
      fields.put("amount", command.amount());
      fields.put("expireAt", command.expireAt() == null ? null : command.expireAt().toString());
      fields.put("notifyUrl", command.notifyUrl());
      fields.put("returnUrl", command.returnUrl());
      fields.put("customerReference", command.customerReference());
      fields.put("description", command.description());
      fields.put("payer", command.payer());
      return (objectMapper == null ? new ObjectMapper() : objectMapper).writeValueAsString(fields);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法记录商户请求参数", exception);
    }
  }

  private PaymentOrder applyPricing(
      PaymentOrder order, PlatformChannelConfigurationClient.ResolvedPaymentConfiguration config) {
    var fee = calculateFee(order.amount(), config).add(config.extraFee());
    if (config.minFee() != null && fee.compareTo(config.minFee()) < 0) fee = config.minFee();
    if (config.maxFee() != null && fee.compareTo(config.maxFee()) > 0) fee = config.maxFee();
    fee = fee.setScale(channelAmountScale(config.runtime()), RoundingMode.HALF_UP);
    var normalizedBearer =
        "MERCHANT_BEAR".equals(config.feeMode()) || "INCLUSIVE".equals(config.feeMode())
            ? "MERCHANT"
            : "PAYER";
    var payerPayable = order.amount();
    var net = order.amount();
    if ("MERCHANT".equals(normalizedBearer)) {
      if (fee.compareTo(order.amount()) > 0) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "内含手续费不能超过交易金额");
      }
      net = order.amount().subtract(fee);
    } else {
      payerPayable = order.amount().add(fee);
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
      pricing.put("feeBearer", normalizedBearer);
      pricing.put("supportsRefund", config.supportsRefund());
      pricing.put("feeAmount", fee);
      pricing.put("payerPayableAmount", payerPayable);
      pricing.put("netAmount", net);
      pricing.put("configVersion", config.configVersion());
      var pricingSnapshot = objectMapper.writeValueAsString(pricing);
      if (payerPayable.compareTo(config.channelMinAmount()) < 0
          || payerPayable.compareTo(config.channelMaxAmount()) > 0) {
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "含手续费后的支付金额超出渠道范围");
      }
      return order.withPricing(
          fee, payerPayable, net, normalizedBearer, routeSnapshot, pricingSnapshot);
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

  private int channelAmountScale(ChannelRuntimeContext runtime) {
    String configured = runtime.setting("amountScale");
    if (configured.isBlank()) return 2;
    try {
      int scale = Integer.parseInt(configured);
      if (scale < 0 || scale > 4) throw new NumberFormatException();
      return scale;
    } catch (NumberFormatException exception) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "渠道金额精度配置无效");
    }
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
      Instant expireAt,
      String notifyUrl,
      String returnUrl,
      String customerReference,
      String payoutDestinationRef,
      String description,
      Map<String, String> payer) {
    public CreateOrderCommand {
      if (expireAt == null) expireAt = Instant.now().plus(Duration.ofMinutes(30));
      payer = payer == null ? Map.of() : Map.copyOf(payer);
    }

    public CreateOrderCommand(
        String merchantId,
        String merchantOrderNo,
        String productCode,
        String paymentMethod,
        String country,
        String currency,
        java.math.BigDecimal amount,
        String idempotencyKey,
        Instant expireAt,
        String notifyUrl,
        String returnUrl,
        String customerReference,
        String payoutDestinationRef,
        String description) {
      this(
          merchantId,
          merchantOrderNo,
          productCode,
          paymentMethod,
          country,
          currency,
          amount,
          idempotencyKey,
          expireAt,
          notifyUrl,
          returnUrl,
          customerReference,
          payoutDestinationRef,
          description,
          Map.of());
    }
  }
}
