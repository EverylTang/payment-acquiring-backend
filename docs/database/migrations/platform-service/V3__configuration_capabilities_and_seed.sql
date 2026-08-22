CREATE TABLE IF NOT EXISTS product_capability (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  capability_id VARCHAR(64) NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  country VARCHAR(8) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  payment_method VARCHAR(64) NOT NULL,
  min_amount DECIMAL(20, 2) NOT NULL,
  max_amount DECIMAL(20, 2) NOT NULL,
  supports_refund BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL,
  UNIQUE KEY uk_product_capability_id (capability_id),
  UNIQUE KEY uk_product_capability_scope (product_code, country, currency, payment_method)
);

CREATE TABLE IF NOT EXISTS merchant_product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  binding_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_merchant_product_id (binding_id),
  UNIQUE KEY uk_merchant_product_scope (merchant_id, product_code)
);

CREATE TABLE IF NOT EXISTS channel_capability (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  capability_id VARCHAR(64) NOT NULL,
  channel_id VARCHAR(64) NOT NULL,
  country VARCHAR(8) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  payment_method VARCHAR(64) NOT NULL,
  min_amount DECIMAL(20, 2) NOT NULL,
  max_amount DECIMAL(20, 2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  UNIQUE KEY uk_channel_capability_id (capability_id),
  UNIQUE KEY uk_channel_capability_scope (channel_id, country, currency, payment_method)
);

INSERT IGNORE INTO merchant (merchant_id, name, status, settlement_currency, created_at, updated_at)
VALUES ('merchant-demo', 'Demo Merchant', 'ACTIVE', 'USD', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO logical_product (product_code, name, status, created_at, updated_at)
VALUES ('CARD-US-USD', '美国卡支付', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO product_capability (capability_id, product_code, country, currency, payment_method, min_amount, max_amount, supports_refund, status)
VALUES ('pc-card-us-usd', 'CARD-US-USD', 'US', 'USD', 'CARD', 1.00, 10000.00, TRUE, 'ACTIVE');

INSERT IGNORE INTO merchant_product (binding_id, merchant_id, product_code, status, created_at, updated_at)
VALUES ('mp-demo-card', 'merchant-demo', 'CARD-US-USD', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO channel (channel_id, name, provider, status, weight, config_json, created_at, updated_at)
VALUES ('simulated-channel', '模拟渠道', 'SIMULATED', 'ACTIVE', 100, JSON_OBJECT('mode', 'SIMULATED', 'successRate', 100), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO channel_capability (capability_id, channel_id, country, currency, payment_method, min_amount, max_amount, status)
VALUES ('cc-sim-card-usd', 'simulated-channel', 'US', 'USD', 'CARD', 1.00, 10000.00, 'ACTIVE');

INSERT IGNORE INTO config_release (release_id, version_no, status, config_json, created_by, approved_by, published_at, created_at)
VALUES ('release-initial', 1, 'PUBLISHED', JSON_OBJECT('description', 'initial configuration'), 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO routing_rule (rule_id, release_version, product_code, merchant_id, payment_method, country, currency, channel_id, priority, weight, status)
VALUES ('route-initial', 1, 'CARD-US-USD', 'merchant-demo', 'CARD', 'US', 'USD', 'simulated-channel', 1, 100, 'ACTIVE');

INSERT IGNORE INTO pricing_rule (rule_id, release_version, product_code, merchant_id, currency, fee_rate, fixed_fee, fee_mode, min_amount, max_amount, status)
VALUES ('price-initial', 1, 'CARD-US-USD', 'merchant-demo', 'USD', 0.020000, 0.30, 'INCLUSIVE', 1.00, 10000.00, 'ACTIVE');

INSERT IGNORE INTO risk_policy (policy_id, release_version, name, priority, decision, condition_json, status)
VALUES ('risk-initial', 1, '默认放行策略', 1000, 'PASS', JSON_OBJECT('productCode', 'CARD-US-USD', 'currency', 'USD'), 'ACTIVE');
