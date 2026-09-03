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

-- Operation-level API permissions. Roles assign these permissions; they do not authorize APIs directly.
INSERT INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('auth:me', '查看当前用户', 'AUTH', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('auth:password:change', '修改本人密码', 'AUTH', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:access:list', '查看当前访问权限', 'SYSTEM', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:detail', '查看用户详情', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:password:reset', '重置用户密码', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:user:role:update', '配置用户角色', 'USER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:role:create', '创建角色', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:role:permission:list', '查看角色操作权限', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:role:permission:update', '配置角色操作权限', 'ROLE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:list', '查看菜单', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:create', '创建菜单', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:update', '编辑菜单', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:status', '变更菜单状态', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:delete', '删除菜单', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:resource-type:list', '查看菜单资源类型', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:resource-type:create', '创建资源类型', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:permission:list', '查看菜单操作权限', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:permission:create', '创建菜单操作权限', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:permission:update', '编辑菜单操作权限', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:menu:permission:delete', '删除菜单操作权限', 'MENU', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:permission:list', '查看权限目录', 'SYSTEM', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:data-scope:role:list', '查看角色数据范围', 'DATA_SCOPE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:data-scope:role:update', '配置角色数据范围', 'DATA_SCOPE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:data-scope:user:list', '查看用户数据范围', 'DATA_SCOPE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('system:data-scope:user:update', '配置用户数据范围', 'DATA_SCOPE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('dashboard:overview', '查看运营总览', 'DASHBOARD', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('channel:list', '查看渠道', 'CHANNEL', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('channel:create', '创建渠道', 'CHANNEL', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('channel:status', '变更渠道状态', 'CHANNEL', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('channel:health:list', '查看渠道健康状态', 'CHANNEL', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('routing-rule:list', '查看路由规则', 'ROUTING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('routing-rule:detail', '查看路由规则详情', 'ROUTING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('routing-rule:create', '创建路由规则', 'ROUTING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('routing-rule:update', '编辑路由规则', 'ROUTING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('routing-rule:status', '变更路由规则状态', 'ROUTING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('routing-rule:delete', '删除路由规则', 'ROUTING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('pricing-rule:list', '查看费率规则', 'PRICING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('pricing-rule:detail', '查看费率规则详情', 'PRICING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('pricing-rule:create', '创建费率规则', 'PRICING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('pricing-rule:update', '编辑费率规则', 'PRICING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('pricing-rule:status', '变更费率规则状态', 'PRICING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('pricing-rule:delete', '删除费率规则', 'PRICING_RULE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('risk-policy:list', '查看风控策略', 'RISK_POLICY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('risk-policy:create', '创建风控策略', 'RISK_POLICY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('risk-policy:status', '变更风控策略状态', 'RISK_POLICY', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('configuration:snapshot:list', '查看配置快照', 'CONFIGURATION', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('config-release:list', '查看配置发布单', 'CONFIG_RELEASE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('config-release:create', '创建配置发布单', 'CONFIG_RELEASE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('config-release:submit', '提交配置发布单', 'CONFIG_RELEASE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('config-release:approve', '审批配置发布单', 'CONFIG_RELEASE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('config-release:publish', '发布配置', 'CONFIG_RELEASE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('config-release:diff', '查看配置差异', 'CONFIG_RELEASE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('config-release:rollback', '回滚配置', 'CONFIG_RELEASE', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('audit:list', '查看操作审计', 'AUDIT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:profile:update', '编辑商户资料', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:contact:list', '查看商户联系人', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:callback:list', '查看商户回调配置', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:credential:list', '查看商户凭证', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('order:list', '查看订单', 'ORDER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('order:statistics', '查看订单统计', 'ORDER', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('outbox:list', '查看失败事件', 'OUTBOX', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('outbox:detail', '查看事件详情', 'OUTBOX', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('outbox:redrive', '重放失败事件', 'OUTBOX', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('payment-event:list', '查看失败支付事件', 'PAYMENT_EVENT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('payment-event:detail', '查看支付事件详情', 'PAYMENT_EVENT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('payment-event:replay', '重放支付事件', 'PAYMENT_EVENT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('reconciliation:bill:import', '导入对账单', 'RECONCILIATION', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('reconciliation:difference:list', '查看对账差异', 'RECONCILIATION', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('reconciliation:bill:reconcile', '执行对账', 'RECONCILIATION', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('reconciliation:difference:resolve', '处理对账差异', 'RECONCILIATION', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), resource_type = VALUES(resource_type),
  status = VALUES(status), updated_at = VALUES(updated_at);

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r CROSS JOIN admin_permission p WHERE r.role_code = 'ADMIN';

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code IN ('auth:me', 'auth:password:change', 'system:access:list', 'dashboard:overview',
    'channel:list', 'channel:health:list', 'routing-rule:list', 'routing-rule:detail', 'pricing-rule:list',
    'pricing-rule:detail', 'risk-policy:list', 'configuration:snapshot:list', 'config-release:list',
    'config-release:diff', 'audit:list', 'merchant:profile', 'merchant:contact:list', 'merchant:callback:list',
    'order:list', 'order:statistics')
WHERE r.role_code IN ('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY');

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code IN ('merchant:profile:update', 'merchant:credential:list', 'channel:create',
    'routing-rule:create', 'routing-rule:update', 'pricing-rule:create', 'config-release:create',
    'config-release:submit', 'outbox:list', 'outbox:detail', 'outbox:redrive', 'payment-event:list',
    'payment-event:detail', 'payment-event:replay', 'reconciliation:bill:import',
    'reconciliation:difference:list', 'reconciliation:bill:reconcile', 'reconciliation:difference:resolve')
WHERE r.role_code = 'OPS';

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code IN ('pricing-rule:create', 'pricing-rule:update')
WHERE r.role_code = 'FINANCE';

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code = 'risk-policy:create'
WHERE r.role_code = 'RISK';
