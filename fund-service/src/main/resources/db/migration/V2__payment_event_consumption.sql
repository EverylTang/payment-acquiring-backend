USE pay_fund;

CREATE TABLE IF NOT EXISTS payment_event_consumption (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  attempt_id VARCHAR(64),
  merchant_id VARCHAR(64) NOT NULL,
  amount DECIMAL(20, 2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  payload JSON NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  consume_count INT NOT NULL DEFAULT 1,
  first_received_at DATETIME(3) NOT NULL,
  last_received_at DATETIME(3) NOT NULL,
  processed_at DATETIME(3),
  last_error VARCHAR(512),
  ledger_entry_id VARCHAR(64),
  UNIQUE KEY uk_payment_event_consumption (event_id, event_type),
  KEY idx_payment_consumption_status (status, last_received_at)
);
