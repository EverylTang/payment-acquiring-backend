ALTER TABLE payment_outbox_event
  ADD COLUMN claim_token VARCHAR(128) NULL AFTER lock_until,
  ADD KEY idx_outbox_claim_token (claim_token);
