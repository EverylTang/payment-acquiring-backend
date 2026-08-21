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
