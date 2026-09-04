USE pay_platform;

CREATE TABLE IF NOT EXISTS risk_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NULL,
  merchant_id VARCHAR(64) NOT NULL,
  policy_id VARCHAR(64) NULL,
  policy_name VARCHAR(128) NULL,
  decision VARCHAR(16) NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  reason VARCHAR(512) NOT NULL,
  subject_type VARCHAR(32) NULL,
  subject_masked VARCHAR(128) NULL,
  reviewer VARCHAR(64) NULL,
  review_decision VARCHAR(16) NULL,
  review_note VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  resolved_at DATETIME(3) NULL,
  UNIQUE KEY uk_risk_event_id (event_id),
  KEY idx_risk_event_queue (status, risk_level, created_at),
  KEY idx_risk_event_merchant (merchant_id, created_at)
);

CREATE TABLE IF NOT EXISTS risk_case (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_id VARCHAR(80) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  assignee VARCHAR(64) NULL,
  decision VARCHAR(16) NULL,
  note VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_risk_case_id (case_id),
  UNIQUE KEY uk_risk_case_event (event_id)
);

CREATE TABLE IF NOT EXISTS risk_list_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entry_id VARCHAR(64) NOT NULL,
  list_type VARCHAR(16) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  label VARCHAR(128) NOT NULL,
  expires_at DATETIME(3) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_risk_list_entry_id (entry_id),
  KEY idx_risk_list_lookup (list_type, subject_type, subject_hash, status)
);

INSERT IGNORE INTO admin_permission(permission_code,permission_name,resource_type,status,created_at,updated_at) VALUES
  ('risk:event:list','查看风险事件','RISK_EVENT','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('risk:event:review','审核风险事件','RISK_EVENT','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('risk:list:list','查看风控名单','RISK_LIST','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('risk:list:manage','维护风控名单','RISK_LIST','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code IN ('risk:event:list','risk:event:review','risk:list:list','risk:list:manage')
WHERE r.role_code IN ('ADMIN','RISK');
