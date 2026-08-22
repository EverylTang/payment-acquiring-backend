CREATE TABLE IF NOT EXISTS refund_event_consumption (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(128) NOT NULL,
  refund_id VARCHAR(64) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  last_error VARCHAR(512),
  consume_count INT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL,
  processed_at DATETIME(3),
  UNIQUE KEY uk_refund_event (event_id)
);
