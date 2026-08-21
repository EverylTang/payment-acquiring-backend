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
