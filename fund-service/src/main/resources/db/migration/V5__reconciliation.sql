CREATE TABLE IF NOT EXISTS settlement_bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_id VARCHAR(64) NOT NULL,
  channel_id VARCHAR(64) NOT NULL,
  bill_date DATE NOT NULL,
  currency VARCHAR(3) NOT NULL,
  total_amount DECIMAL(20,2) NOT NULL,
  total_count INT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'IMPORTED',
  imported_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_settlement_bill (bill_id),
  KEY idx_settlement_date (channel_id, bill_date)
);
CREATE TABLE IF NOT EXISTS reconciliation_difference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  difference_id VARCHAR(64) NOT NULL,
  bill_id VARCHAR(64) NOT NULL,
  difference_type VARCHAR(32) NOT NULL,
  order_id VARCHAR(64),
  expected_amount DECIMAL(20,2),
  actual_amount DECIMAL(20,2),
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  reason VARCHAR(512),
  resolved_by VARCHAR(128),
  resolved_at DATETIME(3),
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_difference_id (difference_id),
  KEY idx_difference_bill (bill_id, status)
);
CREATE TABLE IF NOT EXISTS settlement_bill_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  bill_id VARCHAR(64) NOT NULL,
  channel_order_id VARCHAR(128) NOT NULL,
  merchant_id VARCHAR(64),
  order_id VARCHAR(64),
  transaction_type VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  amount DECIMAL(20,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  UNIQUE KEY uk_bill_line (bill_id, channel_order_id, transaction_type)
);
