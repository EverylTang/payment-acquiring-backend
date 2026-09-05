-- Controlled upgrade for existing pay_fund databases. Run once after backup approval.
USE pay_fund;

CREATE TABLE IF NOT EXISTS merchant_fund_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  balance DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  frozen_balance DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  total_income DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  total_expense DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  version INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_merchant_account (merchant_id, currency),
  KEY idx_merchant_fund_status (status, merchant_id)
);
CREATE TABLE IF NOT EXISTS merchant_settlement_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  detail_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  order_amount DECIMAL(20,4) NOT NULL,
  fee_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  settlement_amount DECIMAL(20,4) NOT NULL,
  refunded_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  currency VARCHAR(3) NOT NULL,
  settlement_cycle VARCHAR(16) NOT NULL,
  auto_settlement BOOLEAN NOT NULL DEFAULT TRUE,
  min_settlement_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  expected_settlement_date DATE NOT NULL,
  actual_settlement_date DATE NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  settlement_batch_id VARCHAR(64) NULL,
  fund_account_entry_id BIGINT NULL,
  remark VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_settlement_detail (detail_id),
  UNIQUE KEY uk_settlement_order (order_id),
  KEY idx_settlement_date (expected_settlement_date, status),
  KEY idx_settlement_batch (settlement_batch_id, status)
);
CREATE TABLE IF NOT EXISTS merchant_settlement_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id VARCHAR(64) NOT NULL,
  settlement_date DATE NOT NULL,
  total_merchants INT NOT NULL DEFAULT 0,
  total_orders INT NOT NULL DEFAULT 0,
  total_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
  start_time DATETIME(3) NULL,
  end_time DATETIME(3) NULL,
  error_message TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_settlement_batch (batch_id),
  KEY idx_settlement_batch_date (settlement_date, status)
);
CREATE TABLE IF NOT EXISTS merchant_settlement_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id VARCHAR(64) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  settlement_cycle VARCHAR(16) NOT NULL DEFAULT 'T1',
  cycle_days INT NOT NULL DEFAULT 1,
  min_settlement_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  fee_rate DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
  auto_settlement BOOLEAN NOT NULL DEFAULT TRUE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  effective_date DATE NOT NULL,
  expire_date DATE NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_merchant_settlement_rule (merchant_id, currency, effective_date),
  KEY idx_settlement_rule_status (status, merchant_id)
);
CREATE TABLE IF NOT EXISTS merchant_fund_transaction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  transaction_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  transaction_type VARCHAR(32) NOT NULL,
  amount DECIMAL(20,4) NOT NULL,
  balance_before DECIMAL(20,4) NOT NULL,
  balance_after DECIMAL(20,4) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  related_order_id VARCHAR(64) NULL,
  related_settlement_id VARCHAR(64) NULL,
  remark VARCHAR(512) NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_fund_transaction (transaction_id),
  UNIQUE KEY uk_fund_idempotency (idempotency_key),
  KEY idx_fund_transaction_account (account_id, created_at)
);

ALTER TABLE merchant_settlement_detail
  ADD COLUMN IF NOT EXISTS refunded_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000 AFTER settlement_amount,
  ADD COLUMN IF NOT EXISTS auto_settlement BOOLEAN NOT NULL DEFAULT TRUE AFTER settlement_cycle,
  ADD COLUMN IF NOT EXISTS min_settlement_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000 AFTER auto_settlement;
