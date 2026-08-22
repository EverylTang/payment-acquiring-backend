package com.example.payments.trade.service.service;

import com.example.payments.trade.service.model.*;
import com.example.payments.trade.service.mapper.PaymentOutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentOutboxAdminService {
  private final PaymentOutboxEventRepository repository;

  public PaymentOutboxAdminService(PaymentOutboxEventRepository repository) { this.repository = repository; }

  public List<PaymentOutboxEventEntity> findDead(int limit) {
    if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid limit");
    return repository.findDead(limit);
  }

  public PaymentOutboxEventEntity find(String eventId) {
    var event = repository.findByEventId(eventId);
    if (event == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "outbox event not found");
    return event;
  }

  @Transactional
  public PaymentOutboxEventEntity redrive(String eventId, String operator, String reason, String requestId) {
    if (reason == null || reason.isBlank() || reason.length() > 512) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "redrive reason is required");
    }
    var event = find(eventId);
    if (!"DEAD".equals(event.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "only DEAD events can be redriven");
    }
    if (!repository.redrive(eventId, Instant.now())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "outbox event changed concurrently");
    }
    repository.insertAudit(eventId, operator, reason, "DEAD", "RETRYING", requestId, Instant.now());
    return find(eventId);
  }
}
