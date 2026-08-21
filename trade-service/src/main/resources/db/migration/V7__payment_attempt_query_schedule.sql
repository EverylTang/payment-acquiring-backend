ALTER TABLE payment_attempt
  ADD COLUMN query_count INT NOT NULL DEFAULT 0 AFTER version,
  ADD COLUMN next_query_at DATETIME(3) NULL AFTER query_count,
  ADD COLUMN last_query_at DATETIME(3) NULL AFTER next_query_at,
  ADD COLUMN query_lock_owner VARCHAR(128) NULL AFTER last_query_at,
  ADD COLUMN query_lock_until DATETIME(3) NULL AFTER query_lock_owner,
  ADD COLUMN query_claim_token VARCHAR(128) NULL AFTER query_lock_until,
  ADD KEY idx_attempt_query_schedule (status, next_query_at, query_lock_until);

UPDATE payment_attempt
SET next_query_at = DATE_ADD(started_at, INTERVAL 5 MINUTE)
WHERE status = 'PROCESSING' AND next_query_at IS NULL;
