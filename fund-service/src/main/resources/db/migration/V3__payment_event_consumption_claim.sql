USE pay_fund;

ALTER TABLE payment_event_consumption
  ADD COLUMN processing_owner VARCHAR(128),
  ADD COLUMN processing_until DATETIME(3),
  ADD KEY idx_payment_consumption_processing (status, processing_until);
