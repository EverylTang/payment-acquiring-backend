package com.example.payments.trade.service.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("expired_payment_success_exception")
public class ExpiredPaymentSuccessExceptionEntity {
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  private String exceptionId;
  private String orderId;
  private String attemptId;
  private String channelId;
  private String channelOrderId;
  private BigDecimal amount;
  private String currency;
  private String status;
  private LocalDateTime detectedAt;
  private String resolution;
  private String resolvedBy;
  private LocalDateTime resolvedAt;
}
