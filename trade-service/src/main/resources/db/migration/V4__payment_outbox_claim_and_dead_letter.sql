USE pay_trade;

ALTER TABLE payment_outbox_event
  ADD COLUMN locked_by VARCHAR(128),
  ADD COLUMN locked_at DATETIME(3),
  ADD COLUMN lock_until DATETIME(3),
  ADD COLUMN last_failure_type VARCHAR(64),
  ADD COLUMN first_failed_at DATETIME(3),
  ADD COLUMN dead_at DATETIME(3),
  ADD KEY idx_outbox_processing_lock (status, lock_until);

ALTER TABLE payment_outbox_event
  MODIFY status VARCHAR(32) NOT NULL;
