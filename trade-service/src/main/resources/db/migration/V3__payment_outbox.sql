USE pay_trade;

CREATE TABLE IF NOT EXISTS payment_outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(128) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(3) NOT NULL,
  last_error VARCHAR(512),
  created_at DATETIME(3) NOT NULL,
  published_at DATETIME(3),
  UNIQUE KEY uk_outbox_event_id (event_id),
  KEY idx_outbox_pending (status, next_retry_at)
);
