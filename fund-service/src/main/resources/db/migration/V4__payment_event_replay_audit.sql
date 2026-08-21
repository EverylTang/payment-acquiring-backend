USE pay_fund;

ALTER TABLE payment_event_consumption
  ADD COLUMN failure_type VARCHAR(32) NULL AFTER last_error;

CREATE TABLE IF NOT EXISTS payment_event_replay_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(128) NOT NULL,
  operator VARCHAR(128) NOT NULL,
  reason VARCHAR(512) NOT NULL,
  request_id VARCHAR(128),
  created_at DATETIME(3) NOT NULL,
  KEY idx_payment_event_replay_audit_event (event_id, created_at)
);
