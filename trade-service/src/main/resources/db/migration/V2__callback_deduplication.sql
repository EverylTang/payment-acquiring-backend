USE pay_trade;

CREATE TABLE IF NOT EXISTS payment_callback_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  callback_id VARCHAR(128) NOT NULL,
  attempt_id VARCHAR(64),
  channel_order_id VARCHAR(128),
  raw_payload TEXT NOT NULL,
  signature VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL,
  received_at DATETIME(3) NOT NULL,
  processed_at DATETIME(3),
  UNIQUE KEY uk_callback_id (callback_id),
  KEY idx_callback_attempt (attempt_id)
);
