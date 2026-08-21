CREATE TABLE IF NOT EXISTS admin_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  menu_code VARCHAR(128) NOT NULL,
  menu_name VARCHAR(128) NOT NULL,
  menu_type VARCHAR(16) NOT NULL,
  route_path VARCHAR(255),
  component_key VARCHAR(255),
  icon VARCHAR(64),
  sort_order INT NOT NULL DEFAULT 0,
  visible BOOLEAN NOT NULL DEFAULT TRUE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_admin_menu_code (menu_code),
  KEY idx_admin_menu_parent (parent_id, sort_order)
);

CREATE TABLE IF NOT EXISTS admin_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(128) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_admin_permission_code (permission_code)
);

CREATE TABLE IF NOT EXISTS admin_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE IF NOT EXISTS admin_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
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
