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
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_merchant_id (merchant_id)
);

CREATE TABLE IF NOT EXISTS logical_product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
  name VARCHAR(128) NOT NULL COMMENT '名称',
  product_type VARCHAR(16) NOT NULL DEFAULT 'PAYIN' COMMENT '产品类型：PAYIN/PAYOUT',
  access_mode VARCHAR(16) COMMENT '收款接入模式：DIRECT/AGGREGATED；出款产品为空',
  default_country VARCHAR(8) NOT NULL DEFAULT 'US' COMMENT '默认国家或地区',
  default_currency VARCHAR(3) NOT NULL DEFAULT 'USD' COMMENT '默认币种',
  description VARCHAR(1000) COMMENT '产品描述',
  statement_descriptor VARCHAR(22) COMMENT '默认账单描述符',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_product_code (product_code),
  KEY idx_logical_product_status_type (status, product_type, updated_at)
);

CREATE TABLE IF NOT EXISTS country_master (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  country_code VARCHAR(2) NOT NULL COMMENT 'ISO 3166-1 alpha-2 国家地区码',
  country_name VARCHAR(128) NOT NULL COMMENT '国家或地区名称',
  region VARCHAR(64) COMMENT '区域',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_country_master_code (country_code)
);
CREATE TABLE IF NOT EXISTS currency_master (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  currency_code VARCHAR(3) NOT NULL COMMENT 'ISO 4217 币种代码',
  currency_name VARCHAR(128) NOT NULL COMMENT '币种名称',
  symbol VARCHAR(16) COMMENT '币种符号',
  decimal_places TINYINT NOT NULL DEFAULT 2 COMMENT '金额小数位',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_currency_master_code (currency_code)
);
CREATE TABLE IF NOT EXISTS country_currency_master (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  country_code VARCHAR(2) NOT NULL COMMENT '国家或地区代码',
  currency_code VARCHAR(3) NOT NULL COMMENT '币种代码',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_country_currency_master_scope (country_code, currency_code),
  KEY idx_country_currency_master_status (status, country_code, currency_code)
);

CREATE TABLE IF NOT EXISTS channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  name VARCHAR(128) NOT NULL COMMENT '名称',
  provider VARCHAR(64) NOT NULL COMMENT '服务商',
  request_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '渠道请求地址',
  signature_profile VARCHAR(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '签名方案标识',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  config_json JSON NOT NULL COMMENT '配置JSON',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_channel_id (channel_id)
);

SET @add_channel_signature_profile_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE channel ADD COLUMN signature_profile VARCHAR(64) NOT NULL DEFAULT ''DEFAULT'' COMMENT ''签名方案标识'' AFTER provider',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'channel' AND column_name = 'signature_profile'
);
PREPARE add_channel_signature_profile_statement FROM @add_channel_signature_profile_sql;
EXECUTE add_channel_signature_profile_statement;
DEALLOCATE PREPARE add_channel_signature_profile_statement;

SET @add_channel_request_url_sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE channel ADD COLUMN request_url VARCHAR(512) NOT NULL DEFAULT '''' COMMENT ''渠道请求地址'' AFTER provider',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'channel' AND column_name = 'request_url'
);
PREPARE add_channel_request_url_statement FROM @add_channel_request_url_sql;
EXECUTE add_channel_request_url_statement;
DEALLOCATE PREPARE add_channel_request_url_statement;

CREATE TABLE IF NOT EXISTS channel_secret_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  binding_id VARCHAR(64) NOT NULL COMMENT '绑定ID',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  credential_role VARCHAR(64) NOT NULL COMMENT '凭据角色',
  secret_ref VARCHAR(512) NOT NULL COMMENT '密钥管理服务引用',
  key_version VARCHAR(64) COMMENT '密钥版本',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
  UNIQUE KEY uk_channel_secret_binding_id (binding_id),
  UNIQUE KEY uk_channel_secret_binding_role (channel_id, credential_role),
  KEY idx_channel_secret_binding_channel (channel_id, status)
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

-- Drop the obsolete channel-level routing weight in initialized environments.
SET @drop_channel_weight_sql = (
  SELECT IF(COUNT(*) = 1, 'ALTER TABLE channel DROP COLUMN weight', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'channel' AND column_name = 'weight'
);
PREPARE drop_channel_weight_statement FROM @drop_channel_weight_sql;
EXECUTE drop_channel_weight_statement;
DEALLOCATE PREPARE drop_channel_weight_statement;

CREATE TABLE IF NOT EXISTS pricing_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  rule_id VARCHAR(64) NOT NULL COMMENT '规则ID',
  release_version BIGINT NOT NULL COMMENT '配置发布版本',
  product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
  merchant_id VARCHAR(64) COMMENT '商户ID',
  channel_id VARCHAR(64) COMMENT '渠道ID，空表示适用全部渠道',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  fee_rate DECIMAL(10, 6) NOT NULL COMMENT '费率',
  fixed_fee DECIMAL(20, 2) NOT NULL COMMENT '固定手续费',
  extra_fee DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '额外手续费',
  min_fee DECIMAL(20, 2) NULL COMMENT '最小手续费',
  max_fee DECIMAL(20, 2) NULL COMMENT '最大手续费',
  fee_type VARCHAR(16) NOT NULL DEFAULT 'COMBINED' COMMENT '手续费类型',
  tiered_fees JSON NULL COMMENT '阶梯手续费配置',
  fee_mode VARCHAR(16) NOT NULL COMMENT '费率模式',
  min_amount DECIMAL(20, 2) COMMENT '最小金额',
  max_amount DECIMAL(20, 2) COMMENT '最大金额',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  UNIQUE KEY uk_pricing_rule_id (rule_id)
);
SET @add_pricing_channel_id_sql = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE pricing_rule ADD COLUMN channel_id VARCHAR(64) NULL COMMENT ''渠道ID，空表示适用全部渠道'' AFTER merchant_id', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pricing_rule' AND column_name = 'channel_id');
PREPARE add_pricing_channel_id_statement FROM @add_pricing_channel_id_sql;
EXECUTE add_pricing_channel_id_statement;
DEALLOCATE PREPARE add_pricing_channel_id_statement;
SET @add_pricing_fee_type_sql = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE pricing_rule ADD COLUMN fee_type VARCHAR(16) NOT NULL DEFAULT ''COMBINED'' COMMENT ''手续费类型'' AFTER fixed_fee', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pricing_rule' AND column_name = 'fee_type');
PREPARE add_pricing_fee_type_statement FROM @add_pricing_fee_type_sql;
EXECUTE add_pricing_fee_type_statement;
DEALLOCATE PREPARE add_pricing_fee_type_statement;
SET @add_pricing_tiered_fees_sql = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE pricing_rule ADD COLUMN tiered_fees JSON NULL COMMENT ''阶梯手续费配置'' AFTER fee_type', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pricing_rule' AND column_name = 'tiered_fees');
PREPARE add_pricing_tiered_fees_statement FROM @add_pricing_tiered_fees_sql;
EXECUTE add_pricing_tiered_fees_statement;
DEALLOCATE PREPARE add_pricing_tiered_fees_statement;
SET @add_pricing_extra_fee_sql = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE pricing_rule ADD COLUMN extra_fee DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT ''额外手续费'' AFTER fixed_fee', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pricing_rule' AND column_name = 'extra_fee');
PREPARE add_pricing_extra_fee_statement FROM @add_pricing_extra_fee_sql;
EXECUTE add_pricing_extra_fee_statement;
DEALLOCATE PREPARE add_pricing_extra_fee_statement;
SET @add_pricing_min_fee_sql = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE pricing_rule ADD COLUMN min_fee DECIMAL(20, 2) NULL COMMENT ''最小手续费'' AFTER extra_fee', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pricing_rule' AND column_name = 'min_fee');
PREPARE add_pricing_min_fee_statement FROM @add_pricing_min_fee_sql;
EXECUTE add_pricing_min_fee_statement;
DEALLOCATE PREPARE add_pricing_min_fee_statement;
SET @add_pricing_max_fee_sql = (SELECT IF(COUNT(*) = 0, 'ALTER TABLE pricing_rule ADD COLUMN max_fee DECIMAL(20, 2) NULL COMMENT ''最大手续费'' AFTER min_fee', 'SELECT 1') FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'pricing_rule' AND column_name = 'max_fee');
PREPARE add_pricing_max_fee_statement FROM @add_pricing_max_fee_sql;
EXECUTE add_pricing_max_fee_statement;
DEALLOCATE PREPARE add_pricing_max_fee_statement;

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

CREATE TABLE IF NOT EXISTS risk_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NULL,
  merchant_id VARCHAR(64) NOT NULL,
  policy_id VARCHAR(64) NULL,
  policy_name VARCHAR(128) NULL,
  decision VARCHAR(16) NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  reason VARCHAR(512) NOT NULL,
  subject_type VARCHAR(32) NULL,
  subject_masked VARCHAR(128) NULL,
  reviewer VARCHAR(64) NULL,
  review_decision VARCHAR(16) NULL,
  review_note VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  resolved_at DATETIME(3) NULL,
  UNIQUE KEY uk_risk_event_id (event_id),
  KEY idx_risk_event_queue (status, risk_level, created_at),
  KEY idx_risk_event_merchant (merchant_id, created_at)
);

CREATE TABLE IF NOT EXISTS risk_case (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_id VARCHAR(80) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  assignee VARCHAR(64) NULL,
  decision VARCHAR(16) NULL,
  note VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_risk_case_id (case_id),
  UNIQUE KEY uk_risk_case_event (event_id)
);

CREATE TABLE IF NOT EXISTS risk_list_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entry_id VARCHAR(64) NOT NULL,
  list_type VARCHAR(16) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_hash CHAR(64) NOT NULL,
  label VARCHAR(128) NOT NULL,
  expires_at DATETIME(3) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_risk_list_entry_id (entry_id),
  KEY idx_risk_list_lookup (list_type, subject_type, subject_hash, status)
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
  payment_method VARCHAR(64) NULL COMMENT '历史支付方式（兼容字段）',
  customer_payment_method VARCHAR(64) NOT NULL COMMENT '对客支付方式',
  channel_payment_method VARCHAR(64) NOT NULL COMMENT '渠道支付方式',
  min_amount DECIMAL(20, 2) NOT NULL COMMENT '最小金额',
  max_amount DECIMAL(20, 2) NOT NULL COMMENT '最大金额',
  supports_refund BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否支持退款',
  status VARCHAR(16) NOT NULL COMMENT '业务状态',
  UNIQUE KEY uk_product_capability_id (capability_id),
  UNIQUE KEY uk_product_capability_method (product_code, customer_payment_method, channel_payment_method)
);

-- Compatibility migration for databases created by older versions of this script.
ALTER TABLE product_capability MODIFY COLUMN payment_method VARCHAR(64) NULL COMMENT '历史支付方式（兼容字段）';
ALTER TABLE product_capability ADD COLUMN IF NOT EXISTS customer_payment_method VARCHAR(64) NULL COMMENT '对客支付方式' AFTER payment_method;
ALTER TABLE product_capability ADD COLUMN IF NOT EXISTS channel_payment_method VARCHAR(64) NULL COMMENT '渠道支付方式' AFTER customer_payment_method;
UPDATE product_capability
SET customer_payment_method = COALESCE(customer_payment_method, payment_method),
    channel_payment_method = COALESCE(channel_payment_method, payment_method)
WHERE customer_payment_method IS NULL OR channel_payment_method IS NULL;
ALTER TABLE product_capability MODIFY COLUMN customer_payment_method VARCHAR(64) NOT NULL COMMENT '对客支付方式';
ALTER TABLE product_capability MODIFY COLUMN channel_payment_method VARCHAR(64) NOT NULL COMMENT '渠道支付方式';
ALTER TABLE product_capability DROP INDEX IF EXISTS uk_product_capability_scope;
ALTER TABLE product_capability DROP INDEX IF EXISTS uk_product_capability_scope_v2;
ALTER TABLE product_capability DROP COLUMN IF EXISTS country;
ALTER TABLE product_capability DROP COLUMN IF EXISTS currency;
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_capability_method
  ON product_capability (product_code, customer_payment_method, channel_payment_method);

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

INSERT IGNORE INTO merchant (merchant_id, name, status, created_at, updated_at)
VALUES ('merchant-demo', 'Demo Merchant', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO country_master(country_code,country_name,region,status,created_at,updated_at) VALUES
  ('US','美国','北美','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('CN','中国','亚太','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('GB','英国','欧洲','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('SG','新加坡','亚太','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('HK','中国香港','亚太','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO currency_master(currency_code,currency_name,symbol,decimal_places,status,created_at,updated_at) VALUES
  ('USD','美元','$',2,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('CNY','人民币','¥',2,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('GBP','英镑','£',2,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('SGD','新加坡元','S$',2,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('HKD','港元','HK$',2,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('JPY','日元','¥',0,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO country_currency_master(country_code,currency_code,status,created_at,updated_at) VALUES
  ('US','USD','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('CN','CNY','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('GB','GBP','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('SG','SGD','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)), ('HK','HKD','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO country_master(country_code,country_name,region,status,created_at,updated_at) VALUES
  ('TW','中国台湾','亚太','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('KR','韩国','亚太','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO currency_master(currency_code,currency_name,symbol,decimal_places,status,created_at,updated_at) VALUES
  ('TWD','新台币','NT$',0,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('KRW','韩元','₩',0,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO country_currency_master(country_code,currency_code,status,created_at,updated_at) VALUES
  ('TW','TWD','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('KR','KRW','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO logical_product (product_code, name, product_type, access_mode, default_country, default_currency, description, statement_descriptor, status, created_at, updated_at)
VALUES ('CARD-US-USD', '美国卡支付', 'PAYIN', 'DIRECT', 'US', 'USD', '面向美国市场的银行卡收款产品', 'DEMO PAYMENT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO product_capability (capability_id, product_code, customer_payment_method, channel_payment_method, min_amount, max_amount, supports_refund, status)
VALUES ('pc-card-us-usd', 'CARD-US-USD', 'CARD', 'CARD', 1.00, 10000.00, TRUE, 'ACTIVE');

INSERT IGNORE INTO merchant_product (binding_id, merchant_id, product_code, status, created_at, updated_at)
VALUES ('mp-demo-card-usd', 'merchant-demo', 'CARD-US-USD', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO channel (channel_id, name, provider, request_url, status, config_json, created_at, updated_at)
VALUES ('simulated-channel', '模拟渠道', 'SIMULATED', 'https://simulated.local', 'ACTIVE', JSON_OBJECT('mode', 'SIMULATED', 'successRate', 100), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

UPDATE channel SET request_url = 'https://simulated.local' WHERE channel_id = 'simulated-channel' AND request_url = '';

UPDATE channel SET signature_profile = 'SIMULATED_SHA256_PREFIX_V1' WHERE channel_id = 'simulated-channel' AND signature_profile = 'DEFAULT';

-- These inactive templates intentionally contain no production credentials. Activate only after
-- the merchant appId and the three credential values are populated through channel administration.
INSERT IGNORE INTO channel (channel_id, name, provider, request_url, signature_profile, status, config_json, created_at, updated_at)
VALUES
  ('payproo-twd-v1', 'PayProo 台湾收单', 'PAYPROO', 'https://api.payproo.tech/twd/collect/apply', 'PAYPROO_RSA_SHA256_V1', 'INACTIVE',
   JSON_OBJECT('settings', JSON_OBJECT('appId','','queryUrl','https://api.payproo.tech/twd/collect/query','amountScale','0','integerAmount','true','connectTimeoutMs','3000','readTimeoutMs','10000','maxOrderValiditySeconds','604800','requirePayUrl','true','callbackSuccessResponse','SUCCESS','requestFields','appId,orderId,name,phone,email,amount,payType,payModel,callBackUrl,subject,userId,subMerchantId,subMerchantName,body,language','requiredFields','appId,orderId,name,phone,email,amount,payType,payModel,callBackUrl,subject','methodMappings',JSON_OBJECT('TWD_VA',JSON_OBJECT('payType','VA','payModel','BANKTRANSFER'),'TWD_OTC',JSON_OBJECT('payType','OTC','payModel','OTC_STORE'),'TWD_JKO',JSON_OBJECT('payType','EWALLET','payModel','JKOPAY','requiredFields',JSON_ARRAY('userId','subMerchantId')),'TWD_CARD',JSON_OBJECT('payType','CARD','payModel','CREDIT_CARD'),'TWD_APPLEPAY',JSON_OBJECT('payType','CARD','payModel','APPLEPAY'))),'credentials',JSON_OBJECT('merchantSecretKey','','merchantPrivateKey','','platformPublicKey','')),
   CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('payproo-krw-v1', 'PayProo 韩国收单', 'PAYPROO', 'https://api.payproo.tech/krw/collect/apply', 'PAYPROO_RSA_SHA256_V1', 'INACTIVE',
   JSON_OBJECT('settings', JSON_OBJECT('appId','','queryUrl','https://api.payproo.tech/krw/collect/query','amountScale','4','integerAmount','false','connectTimeoutMs','3000','readTimeoutMs','10000','maxOrderValiditySeconds','86400','requirePayUrl','true','callbackSuccessResponse','SUCCESS','requestFields','appId,orderId,name,firstName,lastName,phone,email,amount,payType,payModel,callBackUrl,userId,subject,body','requiredFields','appId,orderId,name,amount,payType,payModel,callBackUrl,userId,subject','methodMappings',JSON_OBJECT('KR_NAVERPAY',JSON_OBJECT('payType','EWALLET','payModel','NAVERPAY'),'KR_KAKAOPAY',JSON_OBJECT('payType','EWALLET','payModel','KAKAOPAY'),'KR_SAMSUNPAY',JSON_OBJECT('payType','EWALLET','payModel','SAMSUNPAY'),'KR_TOSS',JSON_OBJECT('payType','EWALLET','payModel','TOSS'),'KR_PAYCO',JSON_OBJECT('payType','EWALLET','payModel','PAYCO'),'KR_BANK_TRANSFER',JSON_OBJECT('payType','BANK_TRANSFER','payModel','BANKTRANSFER'),'KR_VIRTUAL_ACCOUNT',JSON_OBJECT('payType','BANK_TRANSFER','payModel','VIRTUALACCOUNT'),'KR_LOCAL_CARD',JSON_OBJECT('payType','CARD','payModel','LOCALCARD'))),'credentials',JSON_OBJECT('merchantSecretKey','','merchantPrivateKey','','platformPublicKey','')),
   CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('payproo-hkd-v1', 'PayProo 香港收单', 'PAYPROO', 'https://api.payproo.tech/hkd/collect/apply', 'PAYPROO_RSA_SHA256_V1', 'INACTIVE',
   JSON_OBJECT('settings', JSON_OBJECT('appId','','queryUrl','https://api.payproo.tech/hkd/collect/query','amountScale','4','integerAmount','false','connectTimeoutMs','3000','readTimeoutMs','10000','maxOrderValiditySeconds','86400','requirePayUrl','true','callbackSuccessResponse','SUCCESS','requestFields','appId,orderId,name,firstName,lastName,phone,email,amount,payType,payModel,callBackUrl,userId,subject,body','requiredFields','appId,orderId,name,amount,payType,payModel,callBackUrl,userId,subject','methodMappings',JSON_OBJECT('HK_WECHAT',JSON_OBJECT('payType','EWALLET','payModel','WECHAT'),'HK_ALIPAY',JSON_OBJECT('payType','EWALLET','payModel','ALIPAY'),'HK_OCTOPUS',JSON_OBJECT('payType','EWALLET','payModel','OCTOPUS'))),'credentials',JSON_OBJECT('merchantSecretKey','','merchantPrivateKey','','platformPublicKey','')),
   CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO channel_secret_binding (binding_id, channel_id, credential_role, secret_ref, key_version, status, created_at, updated_at)
VALUES
  ('channel-secret-sim-request', 'simulated-channel', 'requestSigningKey', 'vault://secret/data/payments/channels/simulated#requestSigningKey', 'v1', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('channel-secret-sim-callback', 'simulated-channel', 'callbackVerifyKey', 'vault://secret/data/payments/channels/simulated#callbackVerifyKey', 'v1', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO channel_capability (capability_id, channel_id, country, currency, payment_method, min_amount, max_amount, status)
VALUES ('cc-sim-card-usd', 'simulated-channel', 'US', 'USD', 'CARD', 1.00, 10000.00, 'ACTIVE');

INSERT IGNORE INTO config_release (release_id, version_no, status, config_json, created_by, approved_by, published_at, created_at)
VALUES ('release-initial', 1, 'PUBLISHED', JSON_OBJECT('description', 'initial configuration'), 'system', 'system', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO routing_rule (rule_id, release_version, product_code, merchant_id, payment_method, country, currency, channel_id, priority, weight, status)
VALUES ('route-initial', 1, 'CARD-US-USD', 'merchant-demo', 'CARD', 'US', 'USD', 'simulated-channel', 1, 100, 'ACTIVE');

INSERT IGNORE INTO pricing_rule (rule_id, release_version, product_code, merchant_id, channel_id, currency, fee_rate, fixed_fee, fee_mode, min_amount, max_amount, status)
VALUES ('price-initial', 1, 'CARD-US-USD', 'merchant-demo', 'simulated-channel', 'USD', 0.020000, 0.30, 'INCLUSIVE', 1.00, 10000.00, 'ACTIVE');

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

CREATE TABLE IF NOT EXISTS admin_resource_type (
  resource_type VARCHAR(64) NOT NULL COMMENT '资源类型编码',
  resource_name VARCHAR(128) NOT NULL COMMENT '资源类型名称',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  PRIMARY KEY (resource_type)
);

CREATE TABLE IF NOT EXISTS admin_menu_resource_type (
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  resource_type VARCHAR(64) NOT NULL COMMENT '资源类型编码',
  PRIMARY KEY (menu_id, resource_type)
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
  (0, 'merchant', '商户管理', 'PAGE', '/merchants', 'merchants', 'Store', 20, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'product', '产品管理', 'PAGE', '/products', 'products', 'Layers3', 30, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'merchant-product', '商户产品', 'PAGE', '/merchant-products', 'merchant-products', 'Link', 50, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'routing', '路由与渠道', 'PAGE', '/routing', 'routing', 'Network', 60, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'pricing', '费率管理', 'PAGE', '/pricing', 'pricing', 'CircleDollarSign', 70, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'releases', '版本发布', 'PAGE', '/releases', 'releases', 'Layers3', 80, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'risk', '风控工作台', 'PAGE', '/risk', 'risk', 'ShieldCheck', 90, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'trade', '订单管理', 'PAGE', '/orders', 'orders', 'WalletCards', 100, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'operations', '运营处置', 'PAGE', '/operations', 'operations', 'ShieldCheck', 110, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  (0, 'system', '系统管理', 'DIRECTORY', NULL, NULL, 'Settings2', 120, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

UPDATE admin_menu
SET menu_name = '订单管理', updated_at = CURRENT_TIMESTAMP(3)
WHERE menu_code = 'trade' AND menu_name = '订单与支付';

INSERT IGNORE INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
SELECT id, 'system:user', '用户管理', 'PAGE', '/users', 'users', 'Users', 111, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM admin_menu WHERE menu_code = 'system';

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
SELECT r.id, m.id FROM admin_role r CROSS JOIN admin_menu m WHERE r.role_code = 'ADMIN';

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r CROSS JOIN admin_permission p WHERE r.role_code = 'ADMIN';
INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code LIKE 'merchant:%' OR p.permission_code LIKE 'product:%' OR p.permission_code LIKE 'merchant-product:%' WHERE r.role_code = 'OPS';
INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code LIKE 'merchant:%' OR p.permission_code LIKE 'product:%' OR p.permission_code LIKE 'merchant-product:%' WHERE r.role_code IN ('READONLY', 'RISK', 'FINANCE') AND p.permission_code LIKE '%:list';

-- SOURCE: consolidated platform-service V5
INSERT IGNORE INTO admin_menu (parent_id, menu_code, menu_name, menu_type, route_path, component_key, icon, sort_order, visible, status, created_at, updated_at)
SELECT id, 'system:role', '角色权限', 'PAGE', '/roles', 'roles', 'UsersRound', 112, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM admin_menu WHERE menu_code = 'system';

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
SELECT id, 'system:menu', '菜单管理', 'PAGE', '/menus', 'menus', 'Settings2', 113, TRUE, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
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
SELECT r.id, m.id FROM admin_role r JOIN admin_menu m ON m.menu_code IN ('system:role', 'system:menu') WHERE r.role_code = 'ADMIN';

-- SOURCE: consolidated platform-service V7
CREATE TABLE IF NOT EXISTS merchant_profile (
  merchant_id VARCHAR(64) PRIMARY KEY COMMENT '商户ID',
  legal_name VARCHAR(256) NOT NULL COMMENT '法定名称',
  business_type VARCHAR(32) NOT NULL DEFAULT 'COMPANY' COMMENT '商户主体类型',
  registered_country VARCHAR(8) NOT NULL COMMENT '注册国家或地区',
  industry VARCHAR(128) COMMENT '所属行业',
  business_url VARCHAR(1024) COMMENT '商户官网',
  product_description VARCHAR(1000) COMMENT '商品或服务描述',
  statement_descriptor VARCHAR(22) COMMENT '账单描述符',
  support_email VARCHAR(256) COMMENT '客户支持邮箱',
  support_phone VARCHAR(64) COMMENT '客户支持电话',
  support_url VARCHAR(1024) COMMENT '客户支持网址',
  address_line1 VARCHAR(256) COMMENT '注册地址第一行',
  address_line2 VARCHAR(256) COMMENT '注册地址第二行',
  address_city VARCHAR(128) COMMENT '注册城市',
  address_state VARCHAR(128) COMMENT '注册省州',
  address_postal_code VARCHAR(32) COMMENT '注册地址邮编',
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

CREATE TABLE IF NOT EXISTS merchant_credential (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  credential_id VARCHAR(64) NOT NULL COMMENT '凭证ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  credential_type VARCHAR(32) NOT NULL COMMENT '凭证类型',
  secret_hash CHAR(64) NOT NULL COMMENT '密钥哈希',
  secret_ciphertext TEXT NULL COMMENT 'API 密钥 AES-GCM 密文',
  secret_hint VARCHAR(16) NOT NULL COMMENT '密钥提示',
  expires_at DATETIME(3) NULL COMMENT '凭证过期时间',
  ip_allowlist JSON NULL COMMENT '商户 API 源 IP 白名单',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '业务状态',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  rotated_at DATETIME(3) COMMENT '轮换时间',
  revoked_at DATETIME(3) COMMENT '撤销时间',
  UNIQUE KEY uk_merchant_credential_id (credential_id),
  KEY idx_merchant_credential (merchant_id, status)
);

ALTER TABLE merchant_credential ADD COLUMN IF NOT EXISTS secret_ciphertext TEXT NULL COMMENT 'API 密钥 AES-GCM 密文' AFTER secret_hash;
ALTER TABLE merchant_credential ADD COLUMN IF NOT EXISTS expires_at DATETIME(3) NULL COMMENT '凭证过期时间' AFTER secret_hint;
ALTER TABLE merchant_credential ADD COLUMN IF NOT EXISTS ip_allowlist JSON NULL COMMENT '商户 API 源 IP 白名单' AFTER expires_at;

CREATE TABLE IF NOT EXISTS merchant_api_nonce (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  credential_id VARCHAR(64) NOT NULL COMMENT 'API 凭证ID',
  nonce VARCHAR(128) NOT NULL COMMENT '请求随机数',
  expires_at DATETIME(3) NOT NULL COMMENT '过期时间',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_merchant_api_nonce (merchant_id, credential_id, nonce),
  KEY idx_merchant_api_nonce_expire (expires_at)
);

INSERT IGNORE INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
VALUES
  ('merchant:profile', '查看商户资料', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:contact:update', '维护商户联系人', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:credential:rotate', '轮换商户凭证', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)),
  ('merchant:credential:revoke', '撤销商户凭证', 'MERCHANT', 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code IN ('merchant:profile', 'merchant:contact:update', 'merchant:credential:rotate', 'merchant:credential:revoke')
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

-- SOURCE: consolidated platform-service V10
-- Operation-level API permissions. Roles assign these permissions; they do not authorize APIs directly.
INSERT IGNORE INTO admin_permission (permission_code, permission_name, resource_type, status, created_at, updated_at)
SELECT permission_code, permission_name, resource_type, 'ACTIVE', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
FROM (
  SELECT 'auth:me' permission_code, '查看当前用户' permission_name, 'AUTH' resource_type UNION ALL
  SELECT 'auth:password:change', '修改本人密码', 'AUTH' UNION ALL
  SELECT 'system:access:list', '查看当前访问权限', 'SYSTEM' UNION ALL
  SELECT 'system:user:detail', '查看用户详情', 'USER' UNION ALL
  SELECT 'system:user:password:reset', '重置用户密码', 'USER' UNION ALL
  SELECT 'system:user:role:update', '配置用户角色', 'USER' UNION ALL
  SELECT 'system:role:create', '创建角色', 'ROLE' UNION ALL
  SELECT 'system:role:permission:list', '查看角色操作权限', 'ROLE' UNION ALL
  SELECT 'system:role:permission:update', '配置角色操作权限', 'ROLE' UNION ALL
  SELECT 'system:menu:list', '查看菜单', 'MENU' UNION ALL
  SELECT 'system:menu:create', '创建菜单', 'MENU' UNION ALL
  SELECT 'system:menu:update', '编辑菜单', 'MENU' UNION ALL
  SELECT 'system:menu:status', '变更菜单状态', 'MENU' UNION ALL
  SELECT 'system:menu:delete', '删除菜单', 'MENU' UNION ALL
  SELECT 'system:menu:resource-type:list', '查看菜单资源类型', 'MENU' UNION ALL
  SELECT 'system:menu:resource-type:create', '创建资源类型', 'MENU' UNION ALL
  SELECT 'system:menu:permission:list', '查看菜单操作权限', 'MENU' UNION ALL
  SELECT 'system:menu:permission:create', '创建菜单操作权限', 'MENU' UNION ALL
  SELECT 'system:menu:permission:update', '编辑菜单操作权限', 'MENU' UNION ALL
  SELECT 'system:menu:permission:delete', '删除菜单操作权限', 'MENU' UNION ALL
  SELECT 'system:permission:list', '查看权限目录', 'SYSTEM' UNION ALL
  SELECT 'system:data-scope:role:list', '查看角色数据范围', 'DATA_SCOPE' UNION ALL
  SELECT 'system:data-scope:role:update', '配置角色数据范围', 'DATA_SCOPE' UNION ALL
  SELECT 'system:data-scope:user:list', '查看用户数据范围', 'DATA_SCOPE' UNION ALL
  SELECT 'system:data-scope:user:update', '配置用户数据范围', 'DATA_SCOPE' UNION ALL
  SELECT 'dashboard:overview', '查看运营总览', 'DASHBOARD' UNION ALL
  SELECT 'channel:list', '查看渠道', 'CHANNEL' UNION ALL SELECT 'channel:create', '创建渠道', 'CHANNEL' UNION ALL
  SELECT 'channel:status', '变更渠道状态', 'CHANNEL' UNION ALL SELECT 'channel:update', '编辑渠道', 'CHANNEL' UNION ALL SELECT 'channel:health:list', '查看渠道健康状态', 'CHANNEL' UNION ALL
  SELECT 'routing-rule:list', '查看路由规则', 'ROUTING_RULE' UNION ALL SELECT 'routing-rule:detail', '查看路由规则详情', 'ROUTING_RULE' UNION ALL
  SELECT 'routing-rule:create', '创建路由规则', 'ROUTING_RULE' UNION ALL SELECT 'routing-rule:update', '编辑路由规则', 'ROUTING_RULE' UNION ALL
  SELECT 'routing-rule:status', '变更路由规则状态', 'ROUTING_RULE' UNION ALL SELECT 'routing-rule:delete', '删除路由规则', 'ROUTING_RULE' UNION ALL
  SELECT 'pricing-rule:list', '查看费率规则', 'PRICING_RULE' UNION ALL SELECT 'pricing-rule:detail', '查看费率规则详情', 'PRICING_RULE' UNION ALL
  SELECT 'pricing-rule:create', '创建费率规则', 'PRICING_RULE' UNION ALL SELECT 'pricing-rule:update', '编辑费率规则', 'PRICING_RULE' UNION ALL
  SELECT 'pricing-rule:status', '变更费率规则状态', 'PRICING_RULE' UNION ALL SELECT 'pricing-rule:delete', '删除费率规则', 'PRICING_RULE' UNION ALL
  SELECT 'risk-policy:list', '查看风控策略', 'RISK_POLICY' UNION ALL SELECT 'risk-policy:create', '创建风控策略', 'RISK_POLICY' UNION ALL
  SELECT 'risk-policy:update', '编辑风控策略', 'RISK_POLICY' UNION ALL SELECT 'risk-policy:status', '变更风控策略状态', 'RISK_POLICY' UNION ALL
  SELECT 'configuration:snapshot:list', '查看配置快照', 'CONFIGURATION' UNION ALL
  SELECT 'config-release:list', '查看配置发布单', 'CONFIG_RELEASE' UNION ALL SELECT 'config-release:create', '创建配置发布单', 'CONFIG_RELEASE' UNION ALL
  SELECT 'config-release:submit', '提交配置发布单', 'CONFIG_RELEASE' UNION ALL SELECT 'config-release:approve', '审批配置发布单', 'CONFIG_RELEASE' UNION ALL
  SELECT 'config-release:publish', '发布配置', 'CONFIG_RELEASE' UNION ALL SELECT 'config-release:diff', '查看配置差异', 'CONFIG_RELEASE' UNION ALL
  SELECT 'config-release:rollback', '回滚配置', 'CONFIG_RELEASE' UNION ALL SELECT 'audit:list', '查看操作审计', 'AUDIT' UNION ALL
  SELECT 'merchant:profile:update', '编辑商户资料', 'MERCHANT' UNION ALL SELECT 'merchant:contact:list', '查看商户联系人', 'MERCHANT' UNION ALL
  SELECT 'merchant:credential:list', '查看商户凭证', 'MERCHANT' UNION ALL
  SELECT 'order:list', '查看订单', 'ORDER' UNION ALL SELECT 'order:manage', '处置订单', 'ORDER' UNION ALL SELECT 'order:notify', '再次通知商户', 'ORDER' UNION ALL SELECT 'order:statistics', '查看订单统计', 'ORDER' UNION ALL
  SELECT 'outbox:list', '查看失败事件', 'OUTBOX' UNION ALL SELECT 'outbox:detail', '查看事件详情', 'OUTBOX' UNION ALL SELECT 'outbox:redrive', '重放失败事件', 'OUTBOX' UNION ALL
  SELECT 'payment-event:list', '查看失败支付事件', 'PAYMENT_EVENT' UNION ALL SELECT 'payment-event:detail', '查看支付事件详情', 'PAYMENT_EVENT' UNION ALL SELECT 'payment-event:replay', '重放支付事件', 'PAYMENT_EVENT' UNION ALL
  SELECT 'reconciliation:bill:import', '导入对账单', 'RECONCILIATION' UNION ALL SELECT 'reconciliation:bill:list', '查看渠道结算账单', 'RECONCILIATION' UNION ALL SELECT 'reconciliation:bill:detail', '查看渠道结算账单明细', 'RECONCILIATION' UNION ALL SELECT 'reconciliation:difference:list', '查看对账差异', 'RECONCILIATION' UNION ALL
  SELECT 'reconciliation:bill:reconcile', '执行对账', 'RECONCILIATION' UNION ALL SELECT 'reconciliation:difference:resolve', '处理对账差异', 'RECONCILIATION'
) AS permissions;

INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r CROSS JOIN admin_permission p WHERE r.role_code = 'ADMIN';
INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code IN ('auth:me', 'auth:password:change', 'system:access:list', 'dashboard:overview', 'channel:list', 'channel:health:list', 'routing-rule:list', 'routing-rule:detail', 'pricing-rule:list', 'pricing-rule:detail', 'risk-policy:list', 'configuration:snapshot:list', 'config-release:list', 'config-release:diff', 'audit:list', 'merchant:profile', 'merchant:contact:list', 'order:list', 'order:statistics', 'reconciliation:bill:list', 'reconciliation:bill:detail') WHERE r.role_code IN ('ADMIN', 'OPS', 'RISK', 'FINANCE', 'READONLY');
INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code IN ('merchant:profile:update', 'merchant:credential:list', 'channel:create', 'channel:update', 'routing-rule:create', 'routing-rule:update', 'pricing-rule:create', 'risk-policy:update', 'config-release:create', 'config-release:submit', 'order:manage', 'order:notify', 'outbox:list', 'outbox:detail', 'outbox:redrive', 'payment-event:list', 'payment-event:detail', 'payment-event:replay', 'reconciliation:bill:import', 'reconciliation:bill:list', 'reconciliation:bill:detail', 'reconciliation:difference:list', 'reconciliation:bill:reconcile', 'reconciliation:difference:resolve') WHERE r.role_code = 'OPS';
INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code IN ('pricing-rule:create', 'pricing-rule:update') WHERE r.role_code = 'FINANCE';
INSERT IGNORE INTO admin_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM admin_role r JOIN admin_permission p ON p.permission_code IN ('risk-policy:create', 'risk-policy:update') WHERE r.role_code = 'RISK';

-- TRADE SERVICE
USE pay_trade;

-- SOURCE: consolidated trade-service V1
CREATE TABLE IF NOT EXISTS payment_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  order_id VARCHAR(64) NOT NULL COMMENT '平台订单号',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  merchant_order_no VARCHAR(128) NOT NULL COMMENT '商户订单号',
  product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
  order_type VARCHAR(16) NOT NULL COMMENT '订单类型：PAYIN/PAYOUT',
  payment_method VARCHAR(64) NOT NULL COMMENT '对客支付方式',
  country VARCHAR(8) COMMENT '支付国家或地区',
  currency VARCHAR(3) NOT NULL COMMENT '交易币种',
  amount DECIMAL(20, 2) NOT NULL COMMENT '商户订单基础金额',
  fee_amount DECIMAL(20, 2) NOT NULL DEFAULT 0 COMMENT '匹配费率后的手续费',
  payer_payable_amount DECIMAL(20, 2) NOT NULL COMMENT '付款方实际支付金额',
  net_amount DECIMAL(20, 2) NOT NULL COMMENT '商户应结算净额',
  fee_bearer VARCHAR(16) NOT NULL COMMENT '费用承担方：PAYER/MERCHANT',
  status VARCHAR(32) NOT NULL COMMENT '订单状态',
  idempotency_key VARCHAR(128) NOT NULL COMMENT '请求幂等键',
  merchant_request_snapshot JSON NULL COMMENT '商户创建订单请求快照（已脱敏）',
  route_snapshot_json JSON NOT NULL COMMENT '路由配置快照',
  pricing_snapshot_json JSON NOT NULL COMMENT '费率及金额计算快照',
  expire_at DATETIME(3) NOT NULL COMMENT '订单过期时间',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  paid_at DATETIME(3) COMMENT '支付成功时间',
  payment_token VARCHAR(256) COMMENT '支付令牌',
  notify_url VARCHAR(1024) COMMENT '商户异步通知地址快照',
  return_url VARCHAR(1024) COMMENT '支付完成跳转地址快照',
  customer_reference VARCHAR(128) COMMENT '付款人脱敏引用',
  payout_destination_ref VARCHAR(128) COMMENT '出款收款方脱敏引用',
  description VARCHAR(1000) COMMENT '订单描述',
  callback_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED' COMMENT '商户通知状态',
  callback_event_id VARCHAR(128) NULL COMMENT '最近一次商户通知事件',
  callback_attempt_count INT NOT NULL DEFAULT 0 COMMENT '最近一次商户通知投递次数',
  callback_last_notified_at DATETIME(3) NULL COMMENT '最近一次商户通知投递时间',
  callback_last_error VARCHAR(512) NULL COMMENT '最近一次商户通知错误',
  version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_payment_order_id (order_id),
  UNIQUE KEY uk_payment_order_merchant_order (merchant_id, order_type, merchant_order_no),
  UNIQUE KEY uk_payment_order_idempotency (merchant_id, order_type, idempotency_key),
  KEY idx_payment_order_merchant_status_created (merchant_id, status, created_at),
  KEY idx_payment_order_created (created_at)
);

-- Existing environments created before the consolidated order table must retain their data.
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS payer_payable_amount DECIMAL(20, 2) NULL COMMENT '付款方实际支付金额' AFTER fee_amount;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS order_type VARCHAR(16) NOT NULL DEFAULT 'PAYIN' COMMENT '订单类型：PAYIN/PAYOUT' AFTER product_code;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS merchant_request_snapshot JSON NULL COMMENT '商户创建订单请求快照（已脱敏）' AFTER idempotency_key;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS fee_bearer VARCHAR(16) NULL COMMENT '费用承担方：PAYER/MERCHANT' AFTER net_amount;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS notify_url VARCHAR(1024) NULL COMMENT '商户异步通知地址快照' AFTER payment_token;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS return_url VARCHAR(1024) NULL COMMENT '支付完成跳转地址快照' AFTER notify_url;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS customer_reference VARCHAR(128) NULL COMMENT '付款人脱敏引用' AFTER return_url;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS payout_destination_ref VARCHAR(128) NULL COMMENT '出款收款方脱敏引用' AFTER customer_reference;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS description VARCHAR(1000) NULL COMMENT '订单描述' AFTER customer_reference;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS callback_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED' COMMENT '商户通知状态' AFTER description;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS callback_event_id VARCHAR(128) NULL COMMENT '最近一次商户通知事件' AFTER callback_status;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS callback_attempt_count INT NOT NULL DEFAULT 0 COMMENT '最近一次商户通知投递次数' AFTER callback_event_id;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS callback_last_notified_at DATETIME(3) NULL COMMENT '最近一次商户通知投递时间' AFTER callback_attempt_count;
ALTER TABLE payment_order ADD COLUMN IF NOT EXISTS callback_last_error VARCHAR(512) NULL COMMENT '最近一次商户通知错误' AFTER callback_last_notified_at;
UPDATE payment_order
SET payer_payable_amount = amount, fee_bearer = 'MERCHANT'
WHERE payer_payable_amount IS NULL OR fee_bearer IS NULL;
UPDATE payment_order o
JOIN pay_platform.logical_product p ON p.product_code = o.product_code
SET o.order_type = p.product_type
WHERE o.order_type = 'PAYIN' AND p.product_type IN ('PAYIN', 'PAYOUT');
ALTER TABLE payment_order DROP INDEX IF EXISTS uk_payment_order_merchant_order;
ALTER TABLE payment_order DROP INDEX IF EXISTS uk_payment_order_idempotency;
ALTER TABLE payment_order ADD UNIQUE KEY uk_payment_order_merchant_order (merchant_id, order_type, merchant_order_no);
ALTER TABLE payment_order ADD UNIQUE KEY uk_payment_order_idempotency (merchant_id, order_type, idempotency_key);

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

ALTER TABLE payment_attempt
  ADD COLUMN IF NOT EXISTS payment_url VARCHAR(2048) NULL COMMENT '支付跳转地址' AFTER failure_code;
ALTER TABLE payment_attempt
  ADD COLUMN IF NOT EXISTS qr_code TEXT NULL COMMENT '支付二维码原文' AFTER payment_url;

-- Provider contracts may accept four fractional digits. Channel settings still enforce the
-- provider/currency-specific scale (for example TWD remains integer-only).
ALTER TABLE pricing_rule
  MODIFY COLUMN fixed_fee DECIMAL(20, 4) NOT NULL,
  MODIFY COLUMN extra_fee DECIMAL(20, 4) NOT NULL DEFAULT 0,
  MODIFY COLUMN min_fee DECIMAL(20, 4) NULL,
  MODIFY COLUMN max_fee DECIMAL(20, 4) NULL,
  MODIFY COLUMN min_amount DECIMAL(20, 4) NULL,
  MODIFY COLUMN max_amount DECIMAL(20, 4) NULL;
ALTER TABLE product_capability
  MODIFY COLUMN min_amount DECIMAL(20, 4) NOT NULL,
  MODIFY COLUMN max_amount DECIMAL(20, 4) NOT NULL;
ALTER TABLE channel_capability
  MODIFY COLUMN min_amount DECIMAL(20, 4) NOT NULL,
  MODIFY COLUMN max_amount DECIMAL(20, 4) NOT NULL;
ALTER TABLE payment_order
  MODIFY COLUMN amount DECIMAL(20, 4) NOT NULL,
  MODIFY COLUMN fee_amount DECIMAL(20, 4) NOT NULL DEFAULT 0,
  MODIFY COLUMN payer_payable_amount DECIMAL(20, 4) NOT NULL,
  MODIFY COLUMN net_amount DECIMAL(20, 4) NOT NULL;

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

-- SOURCE: consolidated trade-service V8
ALTER TABLE payment_outbox_event
  ADD KEY idx_outbox_notification_schedule (event_type, status, next_retry_at);

-- SOURCE: consolidated trade-service V9
ALTER TABLE payment_order
  ADD KEY idx_payment_order_expiration (status, expire_at);

-- SOURCE: consolidated trade-service V10
CREATE TABLE IF NOT EXISTS expired_payment_success_exception (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  exception_id VARCHAR(96) NOT NULL COMMENT '异常ID',
  order_id VARCHAR(64) NOT NULL COMMENT '本地已过期订单',
  attempt_id VARCHAR(64) NOT NULL COMMENT '渠道成功尝试',
  channel_id VARCHAR(64) NOT NULL COMMENT '渠道ID',
  channel_order_id VARCHAR(128) NOT NULL COMMENT '渠道订单号',
  amount DECIMAL(20, 2) NOT NULL COMMENT '订单金额',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  status VARCHAR(16) NOT NULL COMMENT 'OPEN/RESOLVED',
  detected_at DATETIME(3) NOT NULL COMMENT '发现时间',
  resolution VARCHAR(512) NULL COMMENT '人工处理说明',
  resolved_by VARCHAR(128) NULL COMMENT '处理人',
  resolved_at DATETIME(3) NULL COMMENT '处理时间',
  UNIQUE KEY uk_expired_success_exception_id (exception_id),
  UNIQUE KEY uk_expired_success_attempt (attempt_id),
  KEY idx_expired_success_status_detected (status, detected_at)
);

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
  amount DECIMAL(20, 4) NOT NULL COMMENT '金额',
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

-- Keep Trade precision aligned with channels that accept four fractional digits.
ALTER TABLE payment_refund MODIFY COLUMN amount DECIMAL(20, 4) NOT NULL;

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
  amount DECIMAL(20, 4) NOT NULL COMMENT '金额',
  currency VARCHAR(3) NOT NULL COMMENT '币种',
  available_at DATETIME(3) COMMENT '可用时间',
  idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
  reversal_of VARCHAR(64) COMMENT '冲正来源分录',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_entry_id (entry_id),
  UNIQUE KEY uk_ledger_idempotency (idempotency_key),
  KEY idx_account_created (account_id, created_at),
  KEY idx_account_currency_created (account_id, currency, created_at)
);

-- SOURCE: consolidated fund-service V2

CREATE TABLE IF NOT EXISTS payment_event_consumption (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  event_id VARCHAR(128) NOT NULL COMMENT '事件ID',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  order_id VARCHAR(64) NOT NULL COMMENT '订单ID',
  attempt_id VARCHAR(64) COMMENT '尝试ID',
  merchant_id VARCHAR(64) NOT NULL COMMENT '商户ID',
  amount DECIMAL(20, 4) NOT NULL COMMENT '金额',
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

-- Keep Fund precision aligned with Trade and channels that accept four fractional digits.
ALTER TABLE ledger_entry MODIFY COLUMN amount DECIMAL(20, 4) NOT NULL;
ALTER TABLE payment_event_consumption MODIFY COLUMN amount DECIMAL(20, 4) NOT NULL;

-- TABLE COMMENTS
USE pay_platform;
ALTER TABLE admin_user COMMENT = '平台管理员用户';
ALTER TABLE admin_role COMMENT = '平台管理员角色';
ALTER TABLE admin_user_role COMMENT = '管理员用户与角色关联';
ALTER TABLE config_release COMMENT = '配置发布版本';
ALTER TABLE merchant COMMENT = '商户主表';
ALTER TABLE logical_product COMMENT = '逻辑产品';
ALTER TABLE country_master COMMENT = '国家或地区基础数据';
ALTER TABLE currency_master COMMENT = '币种基础数据';
ALTER TABLE country_currency_master COMMENT = '国家或地区与币种关联基础数据';
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

USE pay_platform;
INSERT IGNORE INTO admin_resource_type(resource_type,resource_name,status) VALUES ('MASTER_DATA','国家与币种基础数据','ACTIVE');
INSERT IGNORE INTO admin_menu(parent_id,menu_code,menu_name,menu_type,route_path,component_key,icon,sort_order,visible,status,created_at,updated_at)
VALUES (0,'master-data','国家与币种','PAGE','/master-data','master-data','MapPinned',40,TRUE,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO admin_menu_resource_type(menu_id,resource_type) SELECT id,'MASTER_DATA' FROM admin_menu WHERE menu_code='master-data';
INSERT IGNORE INTO admin_permission(permission_code,permission_name,resource_type,status,created_at,updated_at) VALUES
('master-data:list','查看国家与币种','MASTER_DATA','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),('master-data:create','新增国家与币种','MASTER_DATA','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),('master-data:update','编辑国家与币种','MASTER_DATA','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),('master-data:status','变更国家与币种状态','MASTER_DATA','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO admin_role_menu(role_id,menu_id) SELECT r.id,m.id FROM admin_role r JOIN admin_menu m ON m.menu_code='master-data' WHERE r.role_code IN ('ADMIN','OPS');
INSERT IGNORE INTO admin_role_permission(role_id,permission_id) SELECT r.id,p.id FROM admin_role r JOIN admin_permission p ON p.permission_code LIKE 'master-data:%' WHERE r.role_code IN ('ADMIN','OPS');
INSERT IGNORE INTO admin_menu(parent_id,menu_code,menu_name,menu_type,route_path,component_key,icon,sort_order,visible,status,created_at,updated_at)
VALUES (0,'releases','版本发布','PAGE','/releases','releases','Layers3',80,TRUE,'ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO admin_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM admin_role r JOIN admin_menu m ON m.menu_code='releases' WHERE r.role_code IN ('ADMIN','OPS','FINANCE','RISK','READONLY');

-- 菜单按收单业务操作顺序展示；同时覆盖已初始化环境的旧排序。
UPDATE admin_menu
SET sort_order = CASE menu_code
  WHEN 'dashboard' THEN 10
  WHEN 'merchant' THEN 20
  WHEN 'product' THEN 30
  WHEN 'master-data' THEN 40
  WHEN 'merchant-product' THEN 50
  WHEN 'routing' THEN 60
  WHEN 'pricing' THEN 70
  WHEN 'releases' THEN 80
  WHEN 'risk' THEN 90
  WHEN 'trade' THEN 100
  WHEN 'operations' THEN 110
  WHEN 'system' THEN 120
  WHEN 'system:user' THEN 121
  WHEN 'system:role' THEN 122
  WHEN 'system:menu' THEN 123
  ELSE sort_order
END,
updated_at = CURRENT_TIMESTAMP(3)
WHERE menu_code IN (
  'dashboard', 'merchant', 'product', 'master-data', 'merchant-product',
  'routing', 'pricing', 'releases', 'risk', 'trade', 'operations', 'system',
  'system:user', 'system:role', 'system:menu'
);
UPDATE admin_menu SET menu_name = '费率管理', updated_at = CURRENT_TIMESTAMP(3) WHERE menu_code = 'pricing';

-- SOURCE: consolidated fund-service V8 merchant settlement
USE pay_fund;
CREATE TABLE IF NOT EXISTS merchant_fund_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  balance DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  frozen_balance DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  total_income DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  total_expense DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  version INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_merchant_account (merchant_id, currency),
  KEY idx_merchant_fund_status (status, merchant_id)
);
CREATE TABLE IF NOT EXISTS merchant_settlement_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  detail_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  order_id VARCHAR(64) NOT NULL,
  order_amount DECIMAL(20,4) NOT NULL,
  fee_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  settlement_amount DECIMAL(20,4) NOT NULL,
  refunded_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  currency VARCHAR(3) NOT NULL,
  settlement_cycle VARCHAR(16) NOT NULL,
  auto_settlement BOOLEAN NOT NULL DEFAULT TRUE,
  min_settlement_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  expected_settlement_date DATE NOT NULL,
  actual_settlement_date DATE NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  settlement_batch_id VARCHAR(64) NULL,
  fund_account_entry_id BIGINT NULL,
  remark VARCHAR(512) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_settlement_detail (detail_id),
  UNIQUE KEY uk_settlement_order (order_id),
  KEY idx_settlement_date (expected_settlement_date, status),
  KEY idx_settlement_batch (settlement_batch_id, status)
);
CREATE TABLE IF NOT EXISTS merchant_settlement_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id VARCHAR(64) NOT NULL,
  settlement_date DATE NOT NULL,
  total_merchants INT NOT NULL DEFAULT 0,
  total_orders INT NOT NULL DEFAULT 0,
  total_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
  start_time DATETIME(3) NULL,
  end_time DATETIME(3) NULL,
  error_message TEXT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_settlement_batch (batch_id),
  KEY idx_settlement_batch_date (settlement_date, status)
);
CREATE TABLE IF NOT EXISTS merchant_settlement_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  merchant_id VARCHAR(64) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  settlement_cycle VARCHAR(16) NOT NULL DEFAULT 'T1',
  cycle_days INT NOT NULL DEFAULT 1,
  min_settlement_amount DECIMAL(20,4) NOT NULL DEFAULT 0.0000,
  fee_rate DECIMAL(10,4) NOT NULL DEFAULT 0.0000,
  auto_settlement BOOLEAN NOT NULL DEFAULT TRUE,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  effective_date DATE NOT NULL,
  expire_date DATE NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_merchant_settlement_rule (merchant_id, currency, effective_date),
  KEY idx_settlement_rule_status (status, merchant_id)
);
CREATE TABLE IF NOT EXISTS merchant_fund_transaction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  transaction_id VARCHAR(64) NOT NULL,
  account_id VARCHAR(64) NOT NULL,
  merchant_id VARCHAR(64) NOT NULL,
  transaction_type VARCHAR(32) NOT NULL,
  amount DECIMAL(20,4) NOT NULL,
  balance_before DECIMAL(20,4) NOT NULL,
  balance_after DECIMAL(20,4) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  related_order_id VARCHAR(64) NULL,
  related_settlement_id VARCHAR(64) NULL,
  remark VARCHAR(512) NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_fund_transaction (transaction_id),
  UNIQUE KEY uk_fund_idempotency (idempotency_key),
  KEY idx_fund_transaction_account (account_id, created_at)
);

USE pay_platform;

-- SOURCE: consolidated platform-service V11 risk workspace
INSERT IGNORE INTO admin_permission(permission_code,permission_name,resource_type,status,created_at,updated_at) VALUES
  ('risk:event:list','查看风险事件','RISK_EVENT','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('risk:event:review','审核风险事件','RISK_EVENT','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('risk:list:list','查看风控名单','RISK_LIST','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('risk:list:manage','维护风控名单','RISK_LIST','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO admin_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code IN ('risk:event:list','risk:event:review','risk:list:list','risk:list:manage')
WHERE r.role_code IN ('ADMIN','RISK');

-- SOURCE: consolidated platform-service V12 settlement administration
INSERT IGNORE INTO admin_permission(permission_code,permission_name,resource_type,status,created_at,updated_at) VALUES
  ('settlement:rule:list','查看商户结算规则','SETTLEMENT','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('settlement:rule:manage','维护商户结算规则','SETTLEMENT','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('settlement:batch:read','查看结算批次','SETTLEMENT','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3)),
  ('settlement:batch:run','手工执行结算批次','SETTLEMENT','ACTIVE',CURRENT_TIMESTAMP(3),CURRENT_TIMESTAMP(3));
INSERT IGNORE INTO admin_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM admin_role r JOIN admin_permission p
  ON p.permission_code IN ('settlement:rule:list','settlement:rule:manage','settlement:batch:read','settlement:batch:run')
WHERE r.role_code IN ('ADMIN','OPS','FINANCE');
