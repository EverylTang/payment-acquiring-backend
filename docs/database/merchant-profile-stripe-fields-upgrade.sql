-- Merchant account fields added for Stripe-style business, customer-support, and address management.
-- Requires MySQL 8.4+ and is safe to run more than once.
USE pay_platform;

ALTER TABLE merchant_profile
  ADD COLUMN IF NOT EXISTS business_type VARCHAR(32) NOT NULL DEFAULT 'COMPANY' COMMENT '商户主体类型' AFTER legal_name,
  ADD COLUMN IF NOT EXISTS business_url VARCHAR(1024) COMMENT '商户官网' AFTER industry,
  ADD COLUMN IF NOT EXISTS product_description VARCHAR(1000) COMMENT '商品或服务描述' AFTER business_url,
  ADD COLUMN IF NOT EXISTS statement_descriptor VARCHAR(22) COMMENT '账单描述符' AFTER product_description,
  ADD COLUMN IF NOT EXISTS support_email VARCHAR(256) COMMENT '客户支持邮箱' AFTER statement_descriptor,
  ADD COLUMN IF NOT EXISTS support_phone VARCHAR(64) COMMENT '客户支持电话' AFTER support_email,
  ADD COLUMN IF NOT EXISTS support_url VARCHAR(1024) COMMENT '客户支持网址' AFTER support_phone,
  ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(256) COMMENT '注册地址第一行' AFTER support_url,
  ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(256) COMMENT '注册地址第二行' AFTER address_line1,
  ADD COLUMN IF NOT EXISTS address_city VARCHAR(128) COMMENT '注册城市' AFTER address_line2,
  ADD COLUMN IF NOT EXISTS address_state VARCHAR(128) COMMENT '注册省州' AFTER address_city,
  ADD COLUMN IF NOT EXISTS address_postal_code VARCHAR(32) COMMENT '注册地址邮编' AFTER address_state;
