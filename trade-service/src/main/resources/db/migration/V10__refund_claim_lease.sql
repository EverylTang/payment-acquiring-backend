ALTER TABLE payment_refund
  ADD COLUMN processing_owner VARCHAR(128) NULL AFTER callback_id,
  ADD COLUMN processing_until DATETIME(3) NULL AFTER processing_owner;
CREATE INDEX idx_refund_execution ON payment_refund (status, next_attempt_at, processing_until);
