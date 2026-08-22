package com.example.payments.fund.service.service;

import com.example.payments.fund.service.mapper.PaymentEventConsumptionMapper;
import com.example.payments.fund.service.model.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentEventReplayAdminService {
  private final PaymentEventConsumptionMapper mapper;
  private final RocketMQTemplate rocketMQTemplate;
  private final String topic;

  public PaymentEventReplayAdminService(
      PaymentEventConsumptionMapper mapper,
      RocketMQTemplate rocketMQTemplate,
      @Value("${fund.payment-success.topic:PAYMENT_SUCCEEDED}") String topic) {
    this.mapper = mapper;
    this.rocketMQTemplate = rocketMQTemplate;
    this.topic = topic;
  }

  public List<PaymentEventConsumptionEntity> findFailed(int limit) {
    if (limit < 1 || limit > 200)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
    return mapper.findFailed(limit);
  }

  public PaymentEventConsumptionEntity find(long id) {
    var record = mapper.selectById(id);
    if (record == null)
      throw new ResponseStatusException( HttpStatus.NOT_FOUND, "payment event consumption not found");
    return record;
  }

  public PaymentEventConsumptionEntity replay( long id, String operator, String reason, String requestId) {
    if (reason == null || reason.isBlank() || reason.length() > 512) {
      throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "reason must contain 1 to 512 characters");
    }
    var record = find(id);
    if ("CONFLICT".equals(record.getFailureType())) {
      throw new ResponseStatusException( HttpStatus.CONFLICT, "conflicting payment events cannot be replayed");
    }
    var now = LocalDateTime.now(ZoneOffset.UTC);
    if (mapper.requestReplay(id, now) != 1) {
      throw new ResponseStatusException( HttpStatus.CONFLICT, "only failed payment events can be replayed");
    }
    mapper.insertReplayAudit(record.getEventId(), operator, reason.trim(), requestId, now);
    try {
      rocketMQTemplate.syncSend(topic, record.getPayload());
    } catch (RuntimeException exception) {
      mapper.replayFailed(id, truncate(exception.getMessage()), LocalDateTime.now(ZoneOffset.UTC));
      throw exception;
    }
    return find(id);
  }

  private static String truncate(String value) {
    if (value == null) return "payment event replay failed";
    return value.length() <= 512 ? value : value.substring(0, 512);
  }
}
