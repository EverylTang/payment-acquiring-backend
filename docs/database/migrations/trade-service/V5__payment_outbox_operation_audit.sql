USE pay_trade;

CREATE TABLE IF NOT EXISTS payment_outbox_operation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(128) NOT NULL,
  operator VARCHAR(128) NOT NULL,
  reason VARCHAR(512) NOT NULL,
  from_status VARCHAR(32) NOT NULL,
  to_status VARCHAR(32) NOT NULL,
  request_id VARCHAR(128),
  created_at DATETIME(3) NOT NULL,
  KEY idx_outbox_audit_event (event_id, created_at)
);
