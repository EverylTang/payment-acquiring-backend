ALTER TABLE payment_refund
  ADD COLUMN channel_refund_id VARCHAR(128) NULL AFTER currency,
  ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status,
  ADD COLUMN next_attempt_at DATETIME(3) NULL AFTER attempt_count,
  ADD COLUMN last_error VARCHAR(512) NULL AFTER next_attempt_at,
  ADD COLUMN callback_id VARCHAR(128) NULL AFTER last_error;
CREATE UNIQUE INDEX uk_refund_callback ON payment_refund (callback_id);
CREATE TABLE IF NOT EXISTS refund_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  attempt_id VARCHAR(64) NOT NULL,
  refund_id VARCHAR(64) NOT NULL,
  channel_id VARCHAR(64) NOT NULL,
  channel_request_no VARCHAR(128),
  attempt_no INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  request_snapshot JSON,
  response_snapshot JSON,
  failure_code VARCHAR(64),
  started_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3),
  UNIQUE KEY uk_refund_attempt (attempt_id),
  KEY idx_refund_attempt (refund_id, attempt_no)
);
CREATE TABLE IF NOT EXISTS refund_callback_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  callback_id VARCHAR(128) NOT NULL,
  refund_id VARCHAR(64) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  processed_at DATETIME(3),
  UNIQUE KEY uk_refund_callback_id (callback_id)
);
