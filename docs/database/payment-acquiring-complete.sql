-- Payment Acquiring complete database bundle
-- Consolidated from the historical service version SQL; this file is the maintained database bundle.
-- Intended for provisioning a new environment; do not rerun blindly on an existing database.
SET NAMES utf8mb4;

-- DATABASES
CREATE DATABASE IF NOT EXISTS pay_platform;
CREATE DATABASE IF NOT EXISTS pay_trade;
CREATE DATABASE IF NOT EXISTS pay_fund;
CREATE DATABASE IF NOT EXISTS pay_audit;

USE pay_platform;

-- PLATFORM SERVICE
-- SOURCE: consolidated platform-service V1
CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  username VARCHAR(64) NOT NULL COMMENT '登录用户名',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_admin_username (username)
);

CREATE TABLE IF NOT EXISTS admin_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
  role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
  UNIQUE KEY uk_admin_role_code (role_code)
);

CREATE TABLE IF NOT EXISTS admin_user_role (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS config_release (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  release_id VARCHAR(64) NOT NULL COMMENT '发布ID',
  version_no BIGINT NOT NULL COMMENT '版本编号',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  config_json JSON NOT NULL COMMENT '配置JSON',
  created_by VARCHAR(64) NOT NULL COMMENT '创建人',
  approved_by VARCHAR(64) COMMENT '审批人',
  published_at DATETIME(3) COMMENT '发布时间',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_release_id (release_id),
  UNIQUE KEY uk_release_version (version_no)
);

CREATE TABLE IF NOT EXISTS merchant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  name VARCHAR(128) NOT NULL COMMENT '名称',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  settlement_currency VARCHAR(3) NOT NULL COMMENT '结算币种',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_merchant_id (merchant_id)
);

CREATE TABLE IF NOT EXISTS logical_product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
  name VARCHAR(128) NOT NULL COMMENT '名称',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_product_code (product_code)
);

CREATE TABLE IF NOT EXISTS channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  name VARCHAR(128) NOT NULL COMMENT '名称',
  provider VARCHAR(64) NOT NULL COMMENT '服务商',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  weight INT NOT NULL DEFAULT 0 COMMENT '权重',
  config_json JSON NOT NULL COMMENT '配置JSON',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_channel_id (channel_id)
);

CREATE TABLE IF NOT EXISTS routing_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  rule_id VARCHAR(64) NOT NULL COMMENT '规则ID',
  release_version BIGINT NOT NULL COMMENT '配置发布版本',
  product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
  merchant_id VARCHAR(64) COMMENT '商户ID',
  payment_method VARCHAR(64) NOT NULL COMMENT '支付方式',
  country VARCHAR(8) COMMENT '国家或地区',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  priority INT NOT NULL COMMENT '优先级',
  weight INT NOT NULL COMMENT '权重',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  UNIQUE KEY uk_routing_rule_id (rule_id)
);

CREATE TABLE IF NOT EXISTS pricing_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  rule_id VARCHAR(64) NOT NULL COMMENT '规则ID',
  release_version BIGINT NOT NULL COMMENT '配置发布版本',
  product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
  merchant_id VARCHAR(64) COMMENT '商户ID',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  fee_rate DECIMAL(10, 6) NOT NULL COMMENT '费率',
  fixed_fee DECIMAL(20, 2) NOT NULL COMMENT '固定手续费',
  fee_mode VARCHAR(16) NOT NULL COMMENT '费率模式',
  min_amount DECIMAL(20, 2) COMMENT '最小金额',
  max_amount DECIMAL(20, 2) COMMENT '最大金额',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  UNIQUE KEY uk_pricing_rule_id (rule_id)
);

CREATE TABLE IF NOT EXISTS risk_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  policy_id VARCHAR(64) NOT NULL COMMENT '策略ID',
  release_version BIGINT NOT NULL COMMENT '配置发布版本',
  name VARCHAR(128) NOT NULL COMMENT '名称',
  priority INT NOT NULL COMMENT '优先级',
  decision VARCHAR(16) NOT NULL COMMENT '风控决策',
  condition_json JSON NOT NULL COMMENT '风控条件配置',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  UNIQUE KEY uk_risk_policy_id (policy_id)
);

INSERT IGNORE INTO admin_role (role_code, role_name) VALUES
  ('ADMIN', '系统管理员'), ('OPS', '运营'), ('RISK', '风控'), ('FINANCE', '财务'), ('READONLY', '只读');

-- SOURCE: consolidated platform-service V2
CREATE TABLE IF NOT EXISTS operation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  audit_id VARCHAR(64) NOT NULL COMMENT '审计ID',
  operator_id VARCHAR(64) NOT NULL COMMENT '操作人ID',
  action VARCHAR(64) NOT NULL COMMENT '操作动作',
  resource_type VARCHAR(64) NOT NULL COMMENT '资源类型',
  resource_id VARCHAR(64) NOT NULL COMMENT '资源ID',
  request_id VARCHAR(128) COMMENT '请求ID',
  reason VARCHAR(512) COMMENT '原因说明',
  before_summary JSON COMMENT '操作前摘要',
  after_summary JSON COMMENT '操作后摘要',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_audit_id (audit_id),
  KEY idx_audit_resource (resource_type, resource_id, created_at),
  KEY idx_audit_operator (operator_id, created_at)
);

-- SOURCE: consolidated platform-service V3
CREATE TABLE IF NOT EXISTS product_capability (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  capability_id VARCHAR(64) NOT NULL COMMENT '能力ID',
  product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
  country VARCHAR(8) NOT NULL COMMENT '国家或地区',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  payment_method VARCHAR(64) NOT NULL COMMENT '支付方式',
  min_amount DECIMAL(20, 2) NOT NULL COMMENT '最小金额',
  max_amount DECIMAL(20, 2) NOT NULL COMMENT '最大金额',
  supports_refund BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否支持退款',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  UNIQUE KEY uk_product_capability_id (capability_id),
  UNIQUE KEY uk_product_capability_scope (product_code, country, currency, payment_method)
);

CREATE TABLE IF NOT EXISTS merchant_product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  binding_id VARCHAR(64) NOT NULL COMMENT '绑定ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_merchant_product_id (binding_id),
  UNIQUE KEY uk_merchant_product_scope (merchant_id, product_code)
);

CREATE TABLE IF NOT EXISTS channel_capability (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  capability_id VARCHAR(64) NOT NULL COMMENT '能力ID',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  country VARCHAR(8) NOT NULL COMMENT '国家或地区',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  payment_method VARCHAR(64) NOT NULL COMMENT '支付方式',
  min_amount DECIMAL(20, 2) NOT NULL COMMENT '最小金额',
  max_amount DECIMAL(20, 2) NOT NULL COMMENT '最大金额',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
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

-- SOURCE: consolidated platform-service V4
CREATE TABLE IF NOT EXISTS admin_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父级ID',
  menu_code VARCHAR(128) NOT NULL COMMENT '菜单编码',
  menu_name VARCHAR(128) NOT NULL COMMENT '菜单名称',
  menu_type VARCHAR(16) NOT NULL COMMENT '菜单类型',
  route_path VARCHAR(255) COMMENT '路由路径',
  component_key VARCHAR(255) COMMENT '组件标识',
  icon VARCHAR(64) COMMENT '菜单图标',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
  visible BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否显示',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_admin_menu_code (menu_code),
  KEY idx_admin_menu_parent (parent_id, sort_order)
);

CREATE TABLE IF NOT EXISTS admin_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
  permission_name VARCHAR(128) NOT NULL COMMENT '权限名称',
  resource_type VARCHAR(64) NOT NULL COMMENT '资源类型',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_admin_permission_code (permission_code)
);

CREATE TABLE IF NOT EXISTS admin_role_menu (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS admin_role_permission (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  permission_id BIGINT NOT NULL COMMENT '权限ID',
  PRIMARY KEY (role_id, permission_id)
);

INSERT IGNORE INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
VALUES
  (0, 'dashboard', '总览', 'PAGE', '/', 'dashboard', 'LayoutDashboard', 10, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'trade', '订单与支付', 'PAGE', '/orders', 'orders', 'WalletCards', 20, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'merchant', '商户管理', 'PAGE', '/merchants', 'merchants', 'Store', 30, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'product', '产品管理', 'PAGE', '/products', 'products', 'Layers3', 40, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'merchant-product', '商户产品', 'PAGE', '/merchant-products', 'merchant-products', 'Link', 50, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'routing', '路由与渠道', 'PAGE', '/routing', 'routing', 'Network', 60, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'pricing', '费率与结算', 'PAGE', '/pricing', 'pricing', 'CircleDollarSign', 70, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'risk', '风控工作台', 'PAGE', '/risk', 'risk', 'ShieldCheck', 80, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'system', '系统管理', 'DIRECTORY', NULL, NULL, 'Settings2', 90, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'system:user', '用户管理', 'PAGE', '/users', 'users', 'Users', 91, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('system:user:list', '查看用户', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:create', '创建用户', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:update', '编辑用户', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:status', '变更用户状态', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:list', '查看商户', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:create', '创建商户', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:status', '变更商户状态', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:list', '查看产品', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:create', '创建产品', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:status', '变更产品状态', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:list', '查看商户产品', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:bind', '绑定商户产品', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM admin_role r CROSS JOIN admin_menu m WHERE r.role_code IN ('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY');

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r CROSS JOIN admin_permission p WHERE r.role_code = 'ADMIN';
INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code LIKE 'merchant:%' OR p.permission_code LIKE 'product:%' OR p.permission_code LIKE 'merchant-product:%' WHERE r.role_code = 'OPS';
INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code LIKE 'merchant:%' OR p.permission_code LIKE 'product:%' OR p.permission_code LIKE 'merchant-product:%' WHERE r.role_code IN ('READONLY', 'RISK', 'FINANCE') AND p.permission_code LIKE '%:list';

-- SOURCE: consolidated platform-service V5
INSERT IGNORE INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
VALUES (0, 'system:role', '角色管理', 'PAGE', '/roles', 'roles', 'UsersRound', 92, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('system:role:list', '查看角色', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:role:update', '编辑角色权限', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM admin_role r JOIN admin_menu m ON m.menu_code = 'system:role' WHERE r.role_code = 'ADMIN';

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code IN ('system:role:list', 'system:role:update') WHERE r.role_code = 'ADMIN';

-- SOURCE: consolidated platform-service V6
INSERT IGNORE INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
SELECT id, 'system:role', '角色权限', 'PAGE', '/roles', 'roles', 'ShieldCheck', 92, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM admin_menu WHERE menu_code = 'system';

INSERT IGNORE INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('system:role:list', '查看角色权限', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:role:update', '配置角色权限', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:detail', '查看商户详情', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:update', '编辑商户', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:detail', '查看产品详情', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:update', '编辑产品', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product-capability:list', '查看产品能力', 'PRODUCT_CAPABILITY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product-capability:create', '创建产品能力', 'PRODUCT_CAPABILITY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product-capability:update', '编辑产品能力', 'PRODUCT_CAPABILITY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product-capability:status', '变更产品能力状态', 'PRODUCT_CAPABILITY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code IN ('system:role:list', 'system:role:update') WHERE r.role_code = 'ADMIN';
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM admin_role r JOIN admin_menu m ON m.menu_code = 'system:role' WHERE r.role_code = 'ADMIN';

-- SOURCE: consolidated platform-service V7
CREATE TABLE IF NOT EXISTS merchant_profile (
  merchant_id VARCHAR(64) PRIMARY KEY COMMENT '商户ID',
  legal_name VARCHAR(256) NOT NULL COMMENT '法定名称',
  registered_country VARCHAR(8) NOT NULL COMMENT '注册国家或地区',
  industry VARCHAR(128) COMMENT '所属行业',
  risk_level VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '风险等级',
  tax_identifier VARCHAR(128) COMMENT '税务识别号',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间'
);

CREATE TABLE IF NOT EXISTS merchant_contact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  contact_type VARCHAR(32) NOT NULL COMMENT '联系人类型',
  contact_name VARCHAR(128) NOT NULL COMMENT '联系人姓名',
  email VARCHAR(256) COMMENT '邮箱地址',
  phone VARCHAR(64) COMMENT '电话号码',
  notify_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用通知',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_merchant_contact_type (merchant_id, contact_type),
  KEY idx_merchant_contact (merchant_id)
);

CREATE TABLE IF NOT EXISTS merchant_callback_config (
  merchant_id VARCHAR(64) PRIMARY KEY COMMENT '商户ID',
  callback_url VARCHAR(1024) NOT NULL COMMENT '回调地址',
  event_types JSON NOT NULL COMMENT '回调事件类型列表',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间'
);

CREATE TABLE IF NOT EXISTS merchant_credential (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  credential_id VARCHAR(64) NOT NULL COMMENT '凭证ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  credential_type VARCHAR(32) NOT NULL COMMENT '凭证类型',
  secret_hash CHAR(64) NOT NULL COMMENT '密钥哈希',
  secret_hint VARCHAR(16) NOT NULL COMMENT '密钥提示',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  rotated_at DATETIME(3) COMMENT '轮换时间',
  revoked_at DATETIME(3) COMMENT '撤销时间',
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

-- SOURCE: consolidated platform-service V8
CREATE TABLE IF NOT EXISTS admin_role_data_scope (
  role_id BIGINT NOT NULL COMMENT '角色ID',
  scope_type VARCHAR(16) NOT NULL COMMENT '数据范围类型',
  PRIMARY KEY (role_id, scope_type)
);

CREATE TABLE IF NOT EXISTS admin_user_merchant_scope (
  user_id BIGINT NOT NULL COMMENT '用户ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  PRIMARY KEY (user_id, merchant_id),
  KEY idx_user_merchant_scope (merchant_id)
);

INSERT IGNORE INTO admin_role_data_scope (role_id, scope_type)
SELECT id, 'ALL' FROM admin_role WHERE role_code = 'ADMIN';

-- Keep the existing OPS behavior until merchant assignments are configured explicitly.
INSERT IGNORE INTO admin_role_data_scope (role_id, scope_type)
SELECT id, 'ALL' FROM admin_role WHERE role_code = 'OPS';

-- SOURCE: consolidated platform-service V9
INSERT IGNORE INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('merchant-product:detail', '查看商户产品详情', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:update', '编辑商户产品', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:status', '变更商户产品状态', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r CROSS JOIN admin_permission p
WHERE r.role_code IN ('ADMIN', 'OPS')
  AND (p.permission_code LIKE 'product-capability:%' OR p.permission_code LIKE 'merchant-product:%');

-- TRADE SERVICE
USE pay_trade;

-- SOURCE: consolidated trade-service V1
CREATE TABLE IF NOT EXISTS payment_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  attempt_id VARCHAR(64) NOT NULL COMMENT '尝试ID',
  order_id VARCHAR(64) NOT NULL COMMENT '订单ID',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  channel_request_no VARCHAR(128) NOT NULL COMMENT '渠道请求号',
  attempt_no INT NOT NULL COMMENT '尝试序号',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  request_summary JSON COMMENT '请求摘要',
  response_summary JSON COMMENT '响应摘要',
  failure_code VARCHAR(64) COMMENT '失败编码',
  started_at DATETIME(3) COMMENT '开始时间',
  completed_at DATETIME(3) COMMENT '完成时间',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_attempt_id (attempt_id),
  UNIQUE KEY uk_channel_request (channel_id, channel_request_no),
  KEY idx_attempt_order (order_id, attempt_no)
);

-- SOURCE: consolidated trade-service V2

CREATE TABLE IF NOT EXISTS payment_callback_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  callback_id VARCHAR(128) NOT NULL COMMENT '回调ID',
  attempt_id VARCHAR(64) COMMENT '尝试ID',
  channel_order_id VARCHAR(128) COMMENT '渠道订单号',
  raw_payload TEXT NOT NULL COMMENT '原始回调数据',
  signature VARCHAR(256) NOT NULL COMMENT '签名',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  received_at DATETIME(3) NOT NULL COMMENT '接收时间',
  processed_at DATETIME(3) COMMENT '处理时间',
  UNIQUE KEY uk_callback_id (callback_id),
  KEY idx_callback_attempt (attempt_id)
);

-- SOURCE: consolidated trade-service V3

CREATE TABLE IF NOT EXISTS payment_outbox_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  event_id VARCHAR(128) NOT NULL COMMENT '事件ID',
  aggregate_type VARCHAR(64) NOT NULL COMMENT '聚合类型',
  aggregate_id VARCHAR(64) NOT NULL COMMENT '聚合ID',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  payload JSON NOT NULL COMMENT '事件数据',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '尝试次数',
  next_retry_at DATETIME(3) NOT NULL COMMENT '下次重试时间',
  last_error VARCHAR(512) COMMENT '错误信息',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  published_at DATETIME(3) COMMENT '发布时间',
  UNIQUE KEY uk_outbox_event_id (event_id),
  KEY idx_outbox_pending (status, next_retry_at)
);

-- SOURCE: consolidated trade-service V4

ALTER TABLE payment_outbox_event
  ADD COLUMN locked_by VARCHAR(128),
  ADD COLUMN locked_at DATETIME(3),
  ADD COLUMN lock_until DATETIME(3),
  ADD COLUMN last_failure_type VARCHAR(64),
  ADD COLUMN first_failed_at DATETIME(3),
  ADD COLUMN dead_at DATETIME(3),
  ADD KEY idx_outbox_processing_lock (status, lock_until);

ALTER TABLE payment_outbox_event
  MODIFY status VARCHAR(32) NOT NULL;

-- SOURCE: consolidated trade-service V5

CREATE TABLE IF NOT EXISTS payment_outbox_operation_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  event_id VARCHAR(128) NOT NULL COMMENT '事件ID',
  operator VARCHAR(128) NOT NULL COMMENT '操作人',
  reason VARCHAR(512) NOT NULL COMMENT '原因说明',
  from_status VARCHAR(32) NOT NULL COMMENT '原状态',
  to_status VARCHAR(32) NOT NULL COMMENT '目标状态',
  request_id VARCHAR(128) COMMENT '请求ID',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  KEY idx_outbox_audit_event (event_id, created_at)
);

-- SOURCE: consolidated trade-service V6
ALTER TABLE payment_outbox_event
  ADD COLUMN claim_token VARCHAR(128) NULL AFTER lock_until,
  ADD KEY idx_outbox_claim_token (claim_token);

-- SOURCE: consolidated trade-service V7
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

-- SOURCE: consolidated trade-service V8
CREATE TABLE IF NOT EXISTS payment_refund (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  refund_id VARCHAR(64) NOT NULL COMMENT '退款ID',
  order_id VARCHAR(64) NOT NULL COMMENT '订单ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
  amount DECIMAL(20, 2) NOT NULL COMMENT '金额',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  reason VARCHAR(512) COMMENT '原因说明',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  completed_at DATETIME(3) COMMENT '完成时间',
  UNIQUE KEY uk_refund_id (refund_id),
  UNIQUE KEY uk_refund_idempotency (merchant_id, idempotency_key),
  KEY idx_refund_order (order_id, created_at)
);

-- SOURCE: consolidated trade-service V9
ALTER TABLE payment_refund
  ADD COLUMN channel_refund_id VARCHAR(128) NULL AFTER currency,
  ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status,
  ADD COLUMN next_attempt_at DATETIME(3) NULL AFTER attempt_count,
  ADD COLUMN last_error VARCHAR(512) NULL AFTER next_attempt_at,
  ADD COLUMN callback_id VARCHAR(128) NULL AFTER last_error;
CREATE UNIQUE INDEX uk_refund_callback ON payment_refund (callback_id);
CREATE TABLE IF NOT EXISTS refund_attempt (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  attempt_id VARCHAR(64) NOT NULL COMMENT '尝试ID',
  refund_id VARCHAR(64) NOT NULL COMMENT '退款ID',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  channel_request_no VARCHAR(128) COMMENT '渠道请求号',
  attempt_no INT NOT NULL COMMENT '尝试序号',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  request_snapshot JSON COMMENT '退款请求快照',
  response_snapshot JSON COMMENT '退款响应快照',
  failure_code VARCHAR(64) COMMENT '失败编码',
  started_at DATETIME(3) NOT NULL COMMENT '开始时间',
  completed_at DATETIME(3) COMMENT '完成时间',
  UNIQUE KEY uk_refund_attempt (attempt_id),
  KEY idx_refund_attempt (refund_id, attempt_no)
);
CREATE TABLE IF NOT EXISTS refund_callback_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  callback_id VARCHAR(128) NOT NULL COMMENT '回调ID',
  refund_id VARCHAR(64) NOT NULL COMMENT '退款ID',
  payload_hash VARCHAR(64) NOT NULL COMMENT '数据哈希',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  processed_at DATETIME(3) COMMENT '处理时间',
  UNIQUE KEY uk_refund_callback_id (callback_id)
);

-- SOURCE: consolidated trade-service V10
ALTER TABLE payment_refund
  ADD COLUMN processing_owner VARCHAR(128) NULL AFTER callback_id,
  ADD COLUMN processing_until DATETIME(3) NULL AFTER processing_owner;
CREATE INDEX idx_refund_execution ON payment_refund (status, next_attempt_at, processing_until);

-- FUND SERVICE
USE pay_fund;

-- SOURCE: consolidated fund-service V1

CREATE TABLE IF NOT EXISTS ledger_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  entry_id VARCHAR(64) NOT NULL COMMENT '台账分录ID',
  account_id VARCHAR(64) NOT NULL COMMENT '资金账户ID',
  order_id VARCHAR(64) COMMENT '订单ID',
  refund_id VARCHAR(64) COMMENT '退款ID',
  entry_type VARCHAR(32) NOT NULL COMMENT '分录类型',
  debit_credit VARCHAR(8) NOT NULL COMMENT '借贷方向',
  amount DECIMAL(20, 2) NOT NULL COMMENT '金额',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  available_at DATETIME(3) COMMENT '可用时间',
  idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
  reversal_of VARCHAR(64) COMMENT '冲正来源分录',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_entry_id (entry_id),
  UNIQUE KEY uk_ledger_idempotency (idempotency_key),
  KEY idx_account_created (account_id, created_at)
);

-- SOURCE: consolidated fund-service V2

CREATE TABLE IF NOT EXISTS payment_event_consumption (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  event_id VARCHAR(128) NOT NULL COMMENT '事件ID',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  order_id VARCHAR(64) NOT NULL COMMENT '订单ID',
  attempt_id VARCHAR(64) COMMENT '尝试ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  amount DECIMAL(20, 2) NOT NULL COMMENT '金额',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  payload JSON NOT NULL COMMENT '事件数据',
  payload_hash CHAR(64) NOT NULL COMMENT '数据哈希',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  consume_count INT NOT NULL DEFAULT 1 COMMENT '消费次数',
  first_received_at DATETIME(3) NOT NULL COMMENT '首次接收时间',
  last_received_at DATETIME(3) NOT NULL COMMENT '最后接收时间',
  processed_at DATETIME(3) COMMENT '处理时间',
  last_error VARCHAR(512) COMMENT '错误信息',
  ledger_entry_id VARCHAR(64) COMMENT '台账分录ID',
  UNIQUE KEY uk_payment_event_consumption (event_id, event_type),
  KEY idx_payment_consumption_status (status, last_received_at)
);

-- SOURCE: consolidated fund-service V3

ALTER TABLE payment_event_consumption
  ADD COLUMN processing_owner VARCHAR(128),
  ADD COLUMN processing_until DATETIME(3),
  ADD KEY idx_payment_consumption_processing (status, processing_until);

-- SOURCE: consolidated fund-service V4

ALTER TABLE payment_event_consumption
  ADD COLUMN failure_type VARCHAR(32) NULL AFTER last_error;

CREATE TABLE IF NOT EXISTS payment_event_replay_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  event_id VARCHAR(128) NOT NULL COMMENT '事件ID',
  operator VARCHAR(128) NOT NULL COMMENT '操作人',
  reason VARCHAR(512) NOT NULL COMMENT '原因说明',
  request_id VARCHAR(128) COMMENT '请求ID',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  KEY idx_payment_event_replay_audit_event (event_id, created_at)
);

-- SOURCE: consolidated fund-service V5
CREATE TABLE IF NOT EXISTS settlement_bill (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  bill_id VARCHAR(64) NOT NULL COMMENT '账单ID',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  bill_date DATE NOT NULL COMMENT '账单日期',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  total_amount DECIMAL(20,2) NOT NULL COMMENT '金额',
  total_count INT NOT NULL COMMENT '总笔数',
  status VARCHAR(16) NOT NULL DEFAULT 'IMPORTED' COMMENT '业务状态',
  imported_at DATETIME(3) NOT NULL COMMENT '导入时间',
  UNIQUE KEY uk_settlement_bill (bill_id),
  KEY idx_settlement_date (channel_id, bill_date)
);
CREATE TABLE IF NOT EXISTS reconciliation_difference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  difference_id VARCHAR(64) NOT NULL COMMENT '差异ID',
  bill_id VARCHAR(64) NOT NULL COMMENT '账单ID',
  difference_type VARCHAR(32) NOT NULL COMMENT '差异类型',
  order_id VARCHAR(64) COMMENT '订单ID',
  expected_amount DECIMAL(20,2) COMMENT '金额',
  actual_amount DECIMAL(20,2) COMMENT '金额',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '业务状态',
  reason VARCHAR(512) COMMENT '原因说明',
  resolved_by VARCHAR(128) COMMENT '解决人',
  resolved_at DATETIME(3) COMMENT '解决时间',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_difference_id (difference_id),
  KEY idx_difference_bill (bill_id, status)
);
CREATE TABLE IF NOT EXISTS settlement_bill_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  bill_id VARCHAR(64) NOT NULL COMMENT '账单ID',
  channel_order_id VARCHAR(128) NOT NULL COMMENT '渠道订单号',
  merchant_id VARCHAR(64) COMMENT '商户ID',
  order_id VARCHAR(64) COMMENT '订单ID',
  transaction_type VARCHAR(16) NOT NULL COMMENT '交易类型',
  status VARCHAR(32) NOT NULL COMMENT '业务状态',
  amount DECIMAL(20,2) NOT NULL COMMENT '金额',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  UNIQUE KEY uk_bill_line (bill_id, channel_order_id, transaction_type)
);

-- SOURCE: consolidated fund-service V6
CREATE TABLE IF NOT EXISTS refund_event_consumption (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  event_id VARCHAR(128) NOT NULL COMMENT '事件ID',
  refund_id VARCHAR(64) NOT NULL COMMENT '退款ID',
  payload_hash VARCHAR(64) NOT NULL COMMENT '数据哈希',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  last_error VARCHAR(512) COMMENT '错误信息',
  consume_count INT NOT NULL DEFAULT 1 COMMENT '消费次数',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  processed_at DATETIME(3) COMMENT '处理时间',
  UNIQUE KEY uk_refund_event (event_id)
);

-- TABLE COMMENTS
USE pay_platform;
ALTER TABLE admin_user COMMENT = '平台管理员用户';
ALTER TABLE admin_role COMMENT = '平台管理员角色';
ALTER TABLE admin_user_role COMMENT = '管理员用户与角色关联';
ALTER TABLE config_release COMMENT = '配置发布版本';
ALTER TABLE merchant COMMENT = '商户主表';
ALTER TABLE logical_product COMMENT = '逻辑产品';
ALTER TABLE channel COMMENT = '支付渠道';
ALTER TABLE routing_rule COMMENT = '支付路由规则';
ALTER TABLE pricing_rule COMMENT = '费率定价规则';
ALTER TABLE risk_policy COMMENT = '风控策略';
ALTER TABLE operation_audit COMMENT = '平台操作审计记录';
ALTER TABLE product_capability COMMENT = '产品支付能力';
ALTER TABLE merchant_product COMMENT = '商户产品绑定';
ALTER TABLE channel_capability COMMENT = '渠道支付能力';
ALTER TABLE admin_menu COMMENT = '后台管理菜单';
ALTER TABLE admin_permission COMMENT = '后台操作权限';
ALTER TABLE admin_role_menu COMMENT = '角色与菜单关联';
ALTER TABLE admin_role_permission COMMENT = '角色与权限关联';
ALTER TABLE merchant_profile COMMENT = '商户资料';
ALTER TABLE merchant_contact COMMENT = '商户联系人';
ALTER TABLE merchant_callback_config COMMENT = '商户回调配置';
ALTER TABLE merchant_credential COMMENT = '商户接入凭证';
ALTER TABLE admin_role_data_scope COMMENT = '角色数据范围';
ALTER TABLE admin_user_merchant_scope COMMENT = '管理员商户数据范围';

USE pay_trade;
ALTER TABLE payment_attempt COMMENT = '支付渠道尝试记录';
ALTER TABLE payment_callback_record COMMENT = '支付渠道回调记录';
ALTER TABLE payment_outbox_event COMMENT = '支付事件发件箱';
ALTER TABLE payment_outbox_operation_audit COMMENT = '发件箱人工操作审计';
ALTER TABLE payment_refund COMMENT = '退款申请与执行记录';
ALTER TABLE refund_attempt COMMENT = '退款渠道尝试记录';
ALTER TABLE refund_callback_record COMMENT = '退款渠道回调记录';

USE pay_fund;
ALTER TABLE ledger_entry COMMENT = '资金台账分录';
ALTER TABLE payment_event_consumption COMMENT = '支付成功事件消费记录';
ALTER TABLE payment_event_replay_audit COMMENT = '支付事件重放审计';
ALTER TABLE settlement_bill COMMENT = '渠道结算账单';
ALTER TABLE reconciliation_difference COMMENT = '对账差异记录';
ALTER TABLE settlement_bill_line COMMENT = '渠道结算账单明细';
ALTER TABLE refund_event_consumption COMMENT = '退款事件消费记录';
