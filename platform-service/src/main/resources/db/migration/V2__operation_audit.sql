CREATE TABLE IF NOT EXISTS operation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  audit_id VARCHAR(64) NOT NULL,
  operator_id VARCHAR(64) NOT NULL,
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(64) NOT NULL,
  request_id VARCHAR(128),
  reason VARCHAR(512),
  before_summary JSON,
  after_summary JSON,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_audit_id (audit_id),
  KEY idx_audit_resource (resource_type, resource_id, created_at),
  KEY idx_audit_operator (operator_id, created_at)
);
