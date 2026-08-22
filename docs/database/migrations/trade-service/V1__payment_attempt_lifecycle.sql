CREATE TABLE IF NOT EXISTS payment_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  attempt_id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  channel_id VARCHAR(64) NOT NULL,
  channel_request_no VARCHAR(128) NOT NULL,
  attempt_no INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  request_summary JSON,
  response_summary JSON,
  failure_code VARCHAR(64),
  started_at DATETIME(3),
  completed_at DATETIME(3),
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_attempt_id (attempt_id),
  UNIQUE KEY uk_channel_request (channel_id, channel_request_no),
  KEY idx_attempt_order (order_id, attempt_no)
);
