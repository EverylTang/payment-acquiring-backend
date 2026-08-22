CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_admin_username (username)
);

CREATE TABLE IF NOT EXISTS admin_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(128) NOT NULL,
  UNIQUE KEY uk_admin_role_code (role_code)
);

CREATE TABLE IF NOT EXISTS admin_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS config_release (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  release_id VARCHAR(64) NOT NULL,
  version_no BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  config_json JSON NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  approved_by VARCHAR(64),
  published_at DATETIME(3),
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_release_id (release_id),
  UNIQUE KEY uk_release_version (version_no)
);

CREATE TABLE IF NOT EXISTS merchant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  settlement_currency VARCHAR(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_merchant_id (merchant_id)
);

CREATE TABLE IF NOT EXISTS logical_product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_product_code (product_code)
);

CREATE TABLE IF NOT EXISTS channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  weight INT NOT NULL DEFAULT 0,
  config_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_channel_id (channel_id)
);

CREATE TABLE IF NOT EXISTS routing_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id VARCHAR(64) NOT NULL,
  release_version BIGINT NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64),
  payment_method VARCHAR(64) NOT NULL,
  country VARCHAR(8),
  currency VARCHAR(3) NOT NULL,
  channel_id VARCHAR(64) NOT NULL,
  priority INT NOT NULL,
  weight INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  UNIQUE KEY uk_routing_rule_id (rule_id)
);

CREATE TABLE IF NOT EXISTS pricing_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id VARCHAR(64) NOT NULL,
  release_version BIGINT NOT NULL,
  product_code VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64),
  currency VARCHAR(3) NOT NULL,
  fee_rate DECIMAL(10, 6) NOT NULL,
  fixed_fee DECIMAL(20, 2) NOT NULL,
  fee_mode VARCHAR(16) NOT NULL,
  min_amount DECIMAL(20, 2),
  max_amount DECIMAL(20, 2),
  status VARCHAR(16) NOT NULL,
  UNIQUE KEY uk_pricing_rule_id (rule_id)
);

CREATE TABLE IF NOT EXISTS risk_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_id VARCHAR(64) NOT NULL,
  release_version BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  priority INT NOT NULL,
  decision VARCHAR(16) NOT NULL,
  condition_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  UNIQUE KEY uk_risk_policy_id (policy_id)
);

INSERT IGNORE INTO admin_role (role_code, role_name) VALUES
  ('ADMIN', '系统管理员'), ('OPS', '运营'), ('RISK', '风控'), ('FINANCE', '财务'), ('READONLY', '只读');
