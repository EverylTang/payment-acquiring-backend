package com.example.payments.platform.service.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Validation shared by both pricing-rule administration endpoints. */
public final class PricingFeeRules {
  public static final String FIXED = "FIXED";
  public static final String PERCENTAGE = "PERCENTAGE";
  public static final String TIERED = "TIERED";
  public static final String COMBINED = "COMBINED";

  private PricingFeeRules() {}

  public static void validate(
      String feeType,
      BigDecimal feeRate,
      BigDecimal fixedFee,
      BigDecimal extraFee,
      BigDecimal minFee,
      BigDecimal maxFee,
      List<FeeTier> tiers) {
    requireNonNegative(feeRate, "比例手续费");
    requireNonNegative(fixedFee, "固定手续费");
    requireNonNegative(extraFee, "额外手续费");
    if (minFee != null) requireNonNegative(minFee, "最小手续费");
    if (maxFee != null) requireNonNegative(maxFee, "最大手续费");
    if (minFee != null && maxFee != null && minFee.compareTo(maxFee) > 0) {
      throw new IllegalArgumentException("最小手续费不能大于最大手续费");
    }
    switch (feeType) {
      case FIXED -> requireZero(feeRate, "固定手续费类型不能填写比例手续费");
      case PERCENTAGE -> requireZero(fixedFee, "比例手续费类型不能填写固定手续费");
      case TIERED -> validateTiers(tiers, feeRate, fixedFee);
      case COMBINED -> {
        if (tiers != null && !tiers.isEmpty()) throw new IllegalArgumentException("组合手续费不支持阶梯配置");
      }
      default -> throw new IllegalArgumentException("不支持的手续费类型: " + feeType);
    }
  }

  private static void validateTiers(List<FeeTier> tiers, BigDecimal feeRate, BigDecimal fixedFee) {
    requireZero(feeRate, "阶梯手续费应在阶梯中配置比例手续费");
    requireZero(fixedFee, "阶梯手续费应在阶梯中配置固定手续费");
    if (tiers == null || tiers.isEmpty()) throw new IllegalArgumentException("阶梯手续费至少需要一个金额区间");
    if (tiers.stream().anyMatch(tier -> tier == null || tier.minAmount() == null)) {
      throw new IllegalArgumentException("阶梯金额区间不完整");
    }
    BigDecimal previousMax = null;
    for (var tier : tiers.stream().sorted(Comparator.comparing(FeeTier::minAmount)).toList()) {
      if (tier == null || tier.minAmount() == null || tier.maxAmount() == null) {
        throw new IllegalArgumentException("阶梯金额区间不完整");
      }
      requireNonNegative(tier.minAmount(), "阶梯最小金额");
      if (tier.maxAmount().compareTo(tier.minAmount()) < 0) {
        throw new IllegalArgumentException("阶梯最大金额不能小于最小金额");
      }
      requireNonNegative(tier.feeRate(), "阶梯比例手续费");
      requireNonNegative(tier.fixedFee(), "阶梯固定手续费");
      if (previousMax != null && tier.minAmount().compareTo(previousMax) <= 0) {
        throw new IllegalArgumentException("阶梯金额区间不能重叠");
      }
      previousMax = tier.maxAmount();
    }
  }

  private static void requireNonNegative(BigDecimal value, String name) {
    if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + "必须大于或等于 0");
  }

  private static void requireZero(BigDecimal value, String message) {
    if (value == null || value.signum() != 0) throw new IllegalArgumentException(message);
  }

  public record FeeTier(
      BigDecimal minAmount, BigDecimal maxAmount, BigDecimal feeRate, BigDecimal fixedFee) {}
}
