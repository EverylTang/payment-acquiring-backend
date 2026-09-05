package com.example.payments.trade.service.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.payments.trade.service.domain.PaymentAttempt;
import com.example.payments.trade.service.domain.PaymentOrder;
import com.example.payments.trade.service.mapper.ExpiredPaymentSuccessExceptionMapper;
import com.example.payments.trade.service.model.ExpiredPaymentSuccessExceptionEntity;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExpiredPaymentSuccessExceptionService {
  private final ExpiredPaymentSuccessExceptionMapper mapper;

  @Transactional
  public void record(PaymentOrder order, PaymentAttempt attempt) {
    var exception = new ExpiredPaymentSuccessExceptionEntity();
    exception.setExceptionId("expired-success-" + UUID.randomUUID());
    exception.setOrderId(order.orderId());
    exception.setAttemptId(attempt.attemptId());
    exception.setChannelId(attempt.channelId());
    exception.setChannelOrderId(attempt.channelRequestNo());
    exception.setAmount(order.amount());
    exception.setCurrency(order.currency());
    exception.setStatus("OPEN");
    exception.setDetectedAt(LocalDateTime.now(ZoneOffset.UTC));
    try {
      mapper.insert(exception);
    } catch (DuplicateKeyException ignored) {
      // The same channel callback can be delivered repeatedly.
    }
  }

  public List<ExpiredPaymentSuccessExceptionEntity> list(String status, int limit) {
    if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid limit");
    return mapper.selectList(
        new LambdaQueryWrapper<ExpiredPaymentSuccessExceptionEntity>()
            .eq(status != null && !status.isBlank(), ExpiredPaymentSuccessExceptionEntity::getStatus, status)
            .orderByAsc(ExpiredPaymentSuccessExceptionEntity::getDetectedAt)
            .last("LIMIT " + limit));
  }

  @Transactional
  public ExpiredPaymentSuccessExceptionEntity resolve(
      String exceptionId, String operator, String resolution) {
    if (resolution == null || resolution.isBlank() || resolution.length() > 512) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resolution is required");
    }
    var exception = mapper.selectOne(new LambdaQueryWrapper<ExpiredPaymentSuccessExceptionEntity>()
        .eq(ExpiredPaymentSuccessExceptionEntity::getExceptionId, exceptionId).last("LIMIT 1"));
    if (exception == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "exception not found");
    if (!"OPEN".equals(exception.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "exception is already resolved");
    }
    exception.setStatus("RESOLVED");
    exception.setResolution(resolution);
    exception.setResolvedBy(operator);
    exception.setResolvedAt(LocalDateTime.now(ZoneOffset.UTC));
    mapper.updateById(exception);
    return exception;
  }
}
