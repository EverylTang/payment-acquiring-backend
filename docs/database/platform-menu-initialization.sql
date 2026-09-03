-- Platform menu and RBAC baseline for an existing pay_platform database.
-- MySQL 8.4+. This script is idempotent and grants the initial navigation only to ADMIN.
USE pay_platform;

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

INSERT INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
VALUES
  (0, 'dashboard', '总览', 'PAGE', '/', 'dashboard', 'LayoutDashboard', 10, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'trade', '订单与支付', 'PAGE', '/orders', 'orders', 'WalletCards', 20, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'merchant', '商户管理', 'PAGE', '/merchants', 'merchants', 'Store', 30, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'product', '产品管理', 'PAGE', '/products', 'products', 'Layers3', 40, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'merchant-product', '商户产品', 'PAGE', '/merchant-products', 'merchant-products', 'Link', 50, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'routing', '路由与渠道', 'PAGE', '/routing', 'routing', 'Network', 60, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'pricing', '费率与结算', 'PAGE', '/pricing', 'pricing', 'CircleDollarSign', 70, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'risk', '风控工作台', 'PAGE', '/risk', 'risk', 'ShieldCheck', 80, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'operations', '运营处置', 'PAGE', '/operations', 'operations', 'ShieldCheck', 85, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'system', '系统管理', 'DIRECTORY', NULL, NULL, 'Settings2', 90, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  menu_name = VALUES(menu_name), menu_type = VALUES(menu_type), route_path = VALUES(route_path),
  component_key = VALUES(component_key), icon = VALUES(icon), sort_order = VALUES(sort_order),
  visible = VALUES(visible), status = VALUES(status), updated_at = VALUES(updated_at);

INSERT INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
SELECT id, 'system:user', '用户管理', 'PAGE', '/users', 'users', 'Users', 91, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM admin_menu WHERE menu_code = 'system'
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  route_path = VALUES(route_path), component_key = VALUES(component_key), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), updated_at = VALUES(updated_at);

INSERT INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
SELECT id, 'system:role', '角色权限', 'PAGE', '/roles', 'roles', 'UsersRound', 92, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM admin_menu WHERE menu_code = 'system'
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  route_path = VALUES(route_path), component_key = VALUES(component_key), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), updated_at = VALUES(updated_at);

INSERT INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
SELECT id, 'system:menu', '菜单管理', 'PAGE', '/menus', 'menus', 'Settings2', 93, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM admin_menu WHERE menu_code = 'system'
ON DUPLICATE KEY UPDATE
  parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), menu_type = VALUES(menu_type),
  route_path = VALUES(route_path), component_key = VALUES(component_key), icon = VALUES(icon),
  sort_order = VALUES(sort_order), visible = VALUES(visible), status = VALUES(status), updated_at = VALUES(updated_at);

INSERT INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('system:user:list', '查看用户', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:create', '创建用户', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:update', '编辑用户', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:status', '变更用户状态', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:role:list', '查看角色权限', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:role:update', '配置角色权限', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:list', '查看商户', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:detail', '查看商户详情', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:create', '创建商户', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:update', '编辑商户', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:status', '变更商户状态', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:profile', '查看商户资料', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:contact:update', '维护商户联系人', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:callback:update', '维护商户回调配置', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:credential:rotate', '轮换商户凭证', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:credential:revoke', '撤销商户凭证', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:list', '查看产品', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:detail', '查看产品详情', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:create', '创建产品', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:update', '编辑产品', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product:status', '变更产品状态', 'PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product-capability:list', '查看产品能力', 'PRODUCT_CAPABILITY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product-capability:create', '创建产品能力', 'PRODUCT_CAPABILITY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product-capability:update', '编辑产品能力', 'PRODUCT_CAPABILITY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('product-capability:status', '变更产品能力状态', 'PRODUCT_CAPABILITY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:list', '查看商户产品', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:detail', '查看商户产品详情', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:bind', '绑定商户产品', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:update', '编辑商户产品', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:status', '变更商户产品状态', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name), resource_type = VALUES(resource_type),
  status = VALUES(status), updated_at = VALUES(updated_at);

INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM admin_role r CROSS JOIN admin_menu m WHERE r.role_code = 'ADMIN';

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r CROSS JOIN admin_permission p WHERE r.role_code = 'ADMIN';

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code LIKE 'merchant:%' OR p.permission_code LIKE 'product:%' OR p.permission_code LIKE 'merchant-product:%'
WHERE r.role_code = 'OPS';

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code LIKE 'merchant:%' OR p.permission_code LIKE 'product:%' OR p.permission_code LIKE 'merchant-product:%'
WHERE r.role_code IN ('READONLY', 'RISK', 'FINANCE') AND p.permission_code LIKE '%:list';
