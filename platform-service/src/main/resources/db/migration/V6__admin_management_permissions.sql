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
