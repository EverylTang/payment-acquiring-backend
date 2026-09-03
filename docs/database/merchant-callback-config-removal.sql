-- 在 pay_platform 数据库执行一次。该操作会永久删除商户回调配置及其数据。
DROP TABLE merchant_callback_config;

DELETE FROM admin_role_permission
WHERE permission_id IN (
  SELECT id FROM admin_permission
  WHERE permission_code IN ('merchant:callback:list', 'merchant:callback:update')
);
DELETE FROM admin_permission
WHERE permission_code IN ('merchant:callback:list', 'merchant:callback:update');
