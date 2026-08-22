CREATE TABLE IF NOT EXISTS admin_role_data_scope (
  role_id BIGINT NOT NULL,
  scope_type VARCHAR(16) NOT NULL,
  PRIMARY KEY (role_id, scope_type)
);

CREATE TABLE IF NOT EXISTS admin_user_merchant_scope (
  user_id BIGINT NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  PRIMARY KEY (user_id, merchant_id),
  KEY idx_user_merchant_scope (merchant_id)
);

INSERT IGNORE INTO admin_role_data_scope (role_id, scope_type)
SELECT id, 'ALL' FROM admin_role WHERE role_code = 'ADMIN';

-- Keep the existing OPS behavior until merchant assignments are configured explicitly.
INSERT IGNORE INTO admin_role_data_scope (role_id, scope_type)
SELECT id, 'ALL' FROM admin_role WHERE role_code = 'OPS';
