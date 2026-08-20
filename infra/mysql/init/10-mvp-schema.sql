USE pay_trade;

CREATE TABLE IF NOT EXISTS payment_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  merchant_order_no VARCHAR(128) NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  payment_method VARCHAR(64) NOT NULL,
  country VARCHAR(8),
  currency VARCHAR(3) NOT NULL,
  amount DECIMAL(20, 2) NOT NULL,
  fee_amount DECIMAL(20, 2) NOT NULL,
  net_amount DECIMAL(20, 2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  route_snapshot_json JSON NOT NULL,
  pricing_snapshot_json JSON NOT NULL,
  expire_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  paid_at DATETIME(3),
  payment_token VARCHAR(128) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_order_id (order_id),
  UNIQUE KEY uk_merchant_order (merchant_id, merchant_order_no),
  UNIQUE KEY uk_idempotency (merchant_id, idempotency_key),
  KEY idx_order_status (merchant_id, status, created_at)
);

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

USE pay_fund;

CREATE TABLE IF NOT EXISTS ledger_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entry_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64),
  refund_id VARCHAR(64),
  entry_type VARCHAR(32) NOT NULL,
  debit_credit VARCHAR(8) NOT NULL,
  amount DECIMAL(20, 2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  available_at DATETIME(3),
  idempotency_key VARCHAR(128) NOT NULL,
  reversal_of VARCHAR(64),
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_entry_id (entry_id),
  UNIQUE KEY uk_ledger_idempotency (idempotency_key),
  KEY idx_account_created (account_id, created_at)
);
