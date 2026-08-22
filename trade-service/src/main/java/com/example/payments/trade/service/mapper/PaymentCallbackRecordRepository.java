package com.example.payments.trade.service.mapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentCallbackRecordRepository {
  private final PaymentCallbackRecordMapper mapper;

  public PaymentCallbackRecordRepository(PaymentCallbackRecordMapper mapper) { this.mapper = mapper; }

  public Optional<PaymentCallbackRecordEntity> findByCallbackId(String callbackId) {
    return Optional.ofNullable(mapper.findByCallbackId(callbackId));
  }

  public boolean claim(String callbackId, String rawPayload, String signature, Instant receivedAt) {
    var entity = new PaymentCallbackRecordEntity();
    entity.setCallbackId(callbackId);
    entity.setRawPayload(rawPayload);
    entity.setSignature(signature);
    entity.setStatus("RECEIVED");
    entity.setReceivedAt(toLocal(receivedAt));
    try {
      mapper.insert(entity);
      return true;
    } catch (DuplicateKeyException duplicate) {
      return false;
    }
  }

  public void markProcessed(String callbackId, String attemptId, String channelOrderId, String status, Instant processedAt) {
    var entity = mapper.findByCallbackId(callbackId);
    entity.setAttemptId(attemptId);
    entity.setChannelOrderId(channelOrderId);
    entity.setStatus(status);
    entity.setProcessedAt(toLocal(processedAt));
    mapper.updateById(entity);
  }

  private static LocalDateTime toLocal(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
