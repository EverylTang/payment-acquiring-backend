package com.example.payments.trade.service.service;

import com.example.payments.trade.service.config.OrderExpirationProperties;
import com.example.payments.trade.service.domain.OrderStatus;
import com.example.payments.trade.service.domain.OrderType;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.PaymentOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
  private final RedisDistributedLockService lockService;

  @Autowired
  public OrderService(
      PaymentOrderRepository repository,
      PlatformChannelConfigurationClient channelConfiguration,
      ObjectMapper objectMapper,
      OrderNumberGenerator orderNumberGenerator,
      MerchantCallbackUrlPolicy callbackUrlPolicy,
      MerchantNotificationOutboxService merchantNotificationOutboxService,
      OrderExpirationProperties expirationProperties,
      RedisDistributedLockService lockService) {
    this.repository = repository;
    this.channelConfiguration = channelConfiguration;
    this.objectMapper = objectMapper;
    this.orderNumberGenerator = orderNumberGenerator;
    this.callbackUrlPolicy = callbackUrlPolicy;
    this.merchantNotificationOutboxService = merchantNotificationOutboxService;
    this.expirationProperties = expirationProperties;
    this.lockService = lockService;
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
    if (lockService == null) {
      return createOrderInternal(command);
    }
    String lockKey =
        "order:create:"
            + command.merchantId()
            + ":"
            + (command.idempotencyKey() != null
                ? command.idempotencyKey()
                : command.merchantOrderNo());
    String requestId = java.util.UUID.randomUUID().toString();

    try {
      return lockService.executeWithLock(
          lockKey,
          requestId,
          Duration.ofSeconds(30),
          Duration.ofSeconds(10),
          () -> createOrderInternal(command));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Order creation interrupted", e);
    }
  }

  @Transactional
  public PaymentOrder createByAppId(CreateOrderCommand command) {
    if (channelConfiguration == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "商户产品解析不可用");
    }
    var productCode =
        channelConfiguration.productCodeByAppId(command.merchantId(), command.appId());
    return create(command.withResolvedProductCode(productCode));
  }

  private PaymentOrder createOrderInternal(CreateOrderCommand command) {
    var existing =
        repository
            .findByIdempotencyForProduct(
                command.merchantId(), command.idempotencyKey(), command.productCode())
            .or(
                () ->
                    repository.findByMerchantOrderForProduct(
                        command.merchantId(), command.merchantOrderNo(), command.productCode()));
    if (existing.isPresent()) {
      return ensureIdempotent(existing.get(), command);
    }
    final var resolvedOrderType =
        channelConfiguration == null
            ? OrderType.PAYIN
            : OrderType.fromProductType(channelConfiguration.productType(command.productCode()));
    if (resolvedOrderType == OrderType.PAYOUT) {
      throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "出款产品尚未启用，不能创建订单");
    }
    if (channelConfiguration != null)
      validateCurrencyScale(command, channelConfiguration.currencyScale(command.currency()));
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
        if (raced.isPresent()) return ensureIdempotent(raced.get(), command);
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
        command.payModel(),
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

  private void validateCurrencyScale(CreateOrderCommand command, Integer decimalPlaces) {
    // Null is retained for isolated unit tests without a platform data source. Production clients
    // fail closed.
    if (decimalPlaces == null) return;
    if (command.amount().stripTrailingZeros().scale() > decimalPlaces) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          command.currency() + " 金额最多允许 " + decimalPlaces + " 位小数");
    }
  }

  public PaymentOrder get(String orderId) {
    return repository
        .findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
  }

  public Map<String, Object> list(
      String merchantId,
      String merchantOrderNo,
      String orderId,
      String productCode,
      String status,
      String currency,
      String orderType,
      LocalDateTime createdFrom,
      LocalDateTime createdTo,
      LocalDateTime paidFrom,
      LocalDateTime paidTo,
      int page,
      int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new IllegalArgumentException("invalid pagination");
    var items =
        repository
            .search(
                merchantId,
                merchantOrderNo,
                orderId,
                productCode,
                status,
                currency,
                orderType,
                createdFrom,
                createdTo,
                paidFrom,
                paidTo,
                page,
                pageSize)
            .stream()
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
        repository.count(
            merchantId,
            merchantOrderNo,
            orderId,
            productCode,
            status,
            currency,
            orderType,
            createdFrom,
            createdTo,
            paidFrom,
            paidTo));
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
    return callbackResult(orderId, status).order();
  }

  /** Indicates whether this callback first transitioned the order to SUCCESS. */
  @Transactional
  public CallbackResult callbackResult(String orderId, OrderStatus status) {
    if (status == OrderStatus.PAYING || status == OrderStatus.CREATED) {
      transition(orderId, status);
      return new CallbackResult(get(orderId), false);
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
      return new CallbackResult(get(orderId), false);
    }
    boolean transitionedToSuccess = false;
    if (current.status().canTransitionTo(status)) {
      transitionedToSuccess =
          repository.updateStatus(
              orderId,
              current.status(),
              status,
              status == OrderStatus.SUCCESS ? Instant.now() : null);
    }
    return new CallbackResult(get(orderId), transitionedToSuccess && status == OrderStatus.SUCCESS);
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
      fields.put("payModel", command.payModel());
      fields.put("country", command.country());
      fields.put("currency", command.currency());
      fields.put("amount", command.amount());
      fields.put("expireAt", command.expireAt() == null ? null : command.expireAt().toString());
      fields.put("expireAtProvided", command.expireAtProvided());
      fields.put("notifyUrl", command.notifyUrl());
      fields.put("returnUrl", command.returnUrl());
      fields.put("customerReference", command.customerReference());
      fields.put("description", command.description());
      fields.put("payer", command.payer());
      fields.put("channelParams", command.channelParams());
      return (objectMapper == null ? new ObjectMapper() : objectMapper).writeValueAsString(fields);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("无法记录商户请求参数", exception);
    }
  }

  private PaymentOrder ensureIdempotent(PaymentOrder existing, CreateOrderCommand command) {
    boolean matches =
        existing.merchantId().equals(command.merchantId())
            && existing.merchantOrderNo().equals(command.merchantOrderNo())
            && existing.productCode().equals(command.productCode())
            && existing.paymentMethod().equals(command.payModel())
            && java.util.Objects.equals(existing.country(), command.country())
            && existing.currency().equals(command.currency())
            && existing.amount().compareTo(command.amount()) == 0
            && java.util.Objects.equals(existing.idempotencyKey(), command.idempotencyKey())
            && java.util.Objects.equals(existing.notifyUrl(), command.notifyUrl())
            && java.util.Objects.equals(existing.returnUrl(), command.returnUrl())
            && java.util.Objects.equals(existing.customerReference(), command.customerReference())
            && java.util.Objects.equals(
                existing.payoutDestinationRef(), command.payoutDestinationRef())
            && java.util.Objects.equals(existing.description(), command.description())
            && expireAtMatches(existing, command)
            && payerMatches(existing, command.payer())
            && channelParamsMatches(existing, command.channelParams());
    if (!matches) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "idempotency key or merchant order conflicts with the original request");
    }
    return existing;
  }

  private boolean expireAtMatches(PaymentOrder existing, CreateOrderCommand command) {
    Boolean existingProvided = expireAtProvided(existing);
    if (existingProvided == null) {
      // Legacy snapshots did not retain whether the merchant supplied the expiry value.
      return !command.expireAtProvided()
          || java.util.Objects.equals(existing.expireAt(), command.expireAt());
    }
    return existingProvided == command.expireAtProvided()
        && (!command.expireAtProvided()
            || java.util.Objects.equals(existing.expireAt(), command.expireAt()));
  }

  private Boolean expireAtProvided(PaymentOrder existing) {
    if (existing.merchantRequestSnapshot() == null
        || existing.merchantRequestSnapshot().isBlank()) {
      return null;
    }
    try {
      var snapshot =
          (objectMapper == null ? new ObjectMapper() : objectMapper)
              .readValue(existing.merchantRequestSnapshot(), Map.class);
      Object value = snapshot.get("expireAtProvided");
      return value instanceof Boolean provided ? provided : null;
    } catch (JsonProcessingException exception) {
      return null;
    }
  }

  private boolean payerMatches(PaymentOrder existing, Map<String, String> payer) {
    if (existing.merchantRequestSnapshot() == null
        || existing.merchantRequestSnapshot().isBlank()) {
      return true;
    }
    try {
      var snapshot =
          (objectMapper == null ? new ObjectMapper() : objectMapper)
              .readValue(existing.merchantRequestSnapshot(), Map.class);
      Object value = snapshot.get("payer");
      if (!(value instanceof Map<?, ?> values)) return payer.isEmpty();
      var existingPayer = new LinkedHashMap<String, String>();
      values.forEach(
          (key, item) -> {
            if (key != null && item != null) {
              existingPayer.put(String.valueOf(key), String.valueOf(item));
            }
          });
      return existingPayer.equals(payer);
    } catch (JsonProcessingException exception) {
      return false;
    }
  }

  private boolean channelParamsMatches(PaymentOrder existing, Map<String, Object> channelParams) {
    if (existing.merchantRequestSnapshot() == null || existing.merchantRequestSnapshot().isBlank()) {
      return channelParams.isEmpty();
    }
    try {
      var snapshot =
          (objectMapper == null ? new ObjectMapper() : objectMapper)
              .readValue(existing.merchantRequestSnapshot(), Map.class);
      Object value = snapshot.get("channelParams");
      return java.util.Objects.equals(value, channelParams)
          || (value == null && channelParams.isEmpty());
    } catch (JsonProcessingException exception) {
      return false;
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
      String payModel,
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
      Map<String, String> payer,
      Map<String, Object> channelParams,
      boolean expireAtProvided) {
    /** The command's third value is the public appId until createByAppId resolves it. */
    public String appId() {
      return productCode;
    }

    public CreateOrderCommand withResolvedProductCode(String resolvedProductCode) {
      return new CreateOrderCommand(
          merchantId,
          merchantOrderNo,
          resolvedProductCode,
          payModel,
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
          payer,
          channelParams,
          expireAtProvided);
    }
    public CreateOrderCommand {
      if (expireAt == null) expireAt = Instant.now().plus(Duration.ofMinutes(30));
      payer = payer == null ? Map.of() : Map.copyOf(payer);
      channelParams = channelParams == null ? Map.of() : Map.copyOf(channelParams);
    }

    public CreateOrderCommand(
        String merchantId,
        String merchantOrderNo,
        String productCode,
        String payModel,
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
        Map<String, String> payer,
        Map<String, Object> channelParams) {
      this(
          merchantId,
          merchantOrderNo,
          productCode,
          payModel,
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
          payer,
          channelParams,
          expireAt != null);
    }

    public CreateOrderCommand(
        String merchantId,
        String merchantOrderNo,
        String productCode,
        String payModel,
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
      this(
          merchantId,
          merchantOrderNo,
          productCode,
          payModel,
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
          payer,
          Map.of());
    }

    public CreateOrderCommand(
        String merchantId,
        String merchantOrderNo,
        String productCode,
        String payModel,
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
          payModel,
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
          Map.of(),
          Map.of(),
          expireAt != null);
    }
  }

  public record CallbackResult(PaymentOrder order, boolean transitionedToSuccess) {}
}
