CREATE TABLE IF NOT EXISTS merchant_profile (
  merchant_id VARCHAR(64) PRIMARY KEY,
  legal_name VARCHAR(256) NOT NULL,
  registered_country VARCHAR(8) NOT NULL,
  industry VARCHAR(128),
  risk_level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
  tax_identifier VARCHAR(128),
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS merchant_contact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id VARCHAR(64) NOT NULL,
  contact_type VARCHAR(32) NOT NULL,
  contact_name VARCHAR(128) NOT NULL,
  email VARCHAR(256),
  phone VARCHAR(64),
  notify_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_merchant_contact_type (merchant_id, contact_type),
  KEY idx_merchant_contact (merchant_id)
);

CREATE TABLE IF NOT EXISTS merchant_callback_config (
  merchant_id VARCHAR(64) PRIMARY KEY,
  callback_url VARCHAR(1024) NOT NULL,
  event_types JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS merchant_credential (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  credential_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  credential_type VARCHAR(32) NOT NULL,
  secret_hash CHAR(64) NOT NULL,
  secret_hint VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  rotated_at DATETIME(3),
  revoked_at DATETIME(3),
  UNIQUE KEY uk_merchant_credential_id (credential_id),
  KEY idx_merchant_credential (merchant_id, status)
);

INSERT IGNORE INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('merchant:profile', '查看商户资料', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:contact:update', '维护商户联系人', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:callback:update', '维护商户回调配置', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:credential:rotate', '轮换商户凭证', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:credential:revoke', '撤销商户凭证', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code IN ('merchant:profile', 'merchant:contact:update', 'merchant:callback:update', 'merchant:credential:rotate', 'merchant:credential:revoke')
WHERE r.role_code IN ('ADMIN', 'OPS');
