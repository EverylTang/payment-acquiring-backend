package com.example.payments.trade.service.service;

import com.example.payments.trade.service.domain.OrderType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.LongSupplier;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Generates sortable, readable IDs without a database round trip.
 *
 * <p>Format: PI/PO + UTC yyyyMMddHHmmssSSS + two-digit node + five-digit sequence.
 */
@Component
public class OrderNumberGenerator {
  private static final int SEQUENCE_LIMIT = 100_000;
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

  private final int nodeId;
  private final String startupId;
  private final LongSupplier currentTimeMillis;
  private long lastMillis = -1;
  private int sequence;

  public OrderNumberGenerator(@Value("${trade.order-id.node-id:0}") int nodeId) {
    this(nodeId, System::currentTimeMillis);
  }

  OrderNumberGenerator(int nodeId, LongSupplier currentTimeMillis) {
    if (nodeId < 0 || nodeId > 99) {
      throw new IllegalArgumentException("trade.order-id.node-id must be between 0 and 99");
    }
    this.nodeId = nodeId;
    this.startupId = String.format("%5s", Long.toString(ThreadLocalRandom.current().nextLong(60_466_176L), 36)).replace(' ', '0');
    this.currentTimeMillis = currentTimeMillis;
  }

  public synchronized String next(OrderType type) {
    long now = currentTimeMillis.getAsLong();
    if (now < lastMillis) now = lastMillis;
    if (now == lastMillis && sequence == SEQUENCE_LIMIT) {
      do {
        now = currentTimeMillis.getAsLong();
      } while (now <= lastMillis);
      sequence = 0;
    } else if (now != lastMillis) {
      sequence = 0;
    }
    lastMillis = now;
    return "%s%s%02d%s%05d".formatted(
        type.numberPrefix(), TIME_FORMAT.format(Instant.ofEpochMilli(now)), nodeId, startupId, sequence++);
  }
}
