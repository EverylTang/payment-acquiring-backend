package com.example.payments.trade.service.service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {
  private static final DefaultRedisScript<Long> COMPARE_AND_DELETE =
      new DefaultRedisScript<>(
          """
          if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
          end
          return 0
          """,
          Long.class);

  private final StringRedisTemplate redisTemplate;

  public boolean tryLock(String key, String requestId, Duration expiration) {
    Boolean success =
        redisTemplate
            .opsForValue()
            .setIfAbsent(key, requestId, expiration.toMillis(), TimeUnit.MILLISECONDS);
    if (Boolean.TRUE.equals(success)) {
      log.debug("Acquired lock: {}", key);
      return true;
    }
    log.debug("Failed to acquire lock: {}", key);
    return false;
  }

  public void unlock(String key, String requestId) {
    Long deleted = redisTemplate.execute(COMPARE_AND_DELETE, List.of(key), requestId);
    if (Long.valueOf(1L).equals(deleted)) {
      log.debug("Released lock: {}", key);
    } else {
      log.debug("Lock was already released or acquired by another request: {}", key);
    }
  }

  public <T> T executeWithLock(
      String key,
      String requestId,
      Duration expiration,
      Duration waitDuration,
      LockAction<T> action)
      throws InterruptedException {
    long startTime = System.currentTimeMillis();
    long waitMillis = waitDuration.toMillis();

    while (System.currentTimeMillis() - startTime < waitMillis) {
      if (tryLock(key, requestId, expiration)) {
        try {
          return action.execute();
        } finally {
          unlock(key, requestId);
        }
      }
      Thread.sleep(50);
    }
    throw new RuntimeException("Failed to acquire lock: " + key);
  }

  @FunctionalInterface
  public interface LockAction<T> {
    T execute();
  }
}
