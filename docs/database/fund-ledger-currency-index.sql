-- 在 pay_fund 数据库执行。
-- 多币种余额必须按商户账户和订单实际币种聚合。
ALTER TABLE ledger_entry
  ADD KEY idx_account_currency_created (account_id, currency, created_at);
