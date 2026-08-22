CREATE TABLE IF NOT EXISTS payment_refund (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  refund_id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  amount DECIMAL(20, 2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason VARCHAR(512),
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3),
  UNIQUE KEY uk_refund_id (refund_id),
  UNIQUE KEY uk_refund_idempotency (merchant_id, idempotency_key),
  KEY idx_refund_order (order_id, created_at)
);
