INSERT IGNORE INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('merchant-product:detail', '查看商户产品详情', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:update', '编辑商户产品', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant-product:status', '变更商户产品状态', 'MERCHANT_PRODUCT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r CROSS JOIN admin_permission p
WHERE r.role_code IN ('ADMIN', 'OPS')
  AND (p.permission_code LIKE 'product-capability:%' OR p.permission_code LIKE 'merchant-product:%');
