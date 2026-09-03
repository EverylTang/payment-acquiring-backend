-- 商户不再维护默认结算币种。
-- 资金账按订单币种分账，订单币种必须来自已绑定产品的已启用产品能力。
-- 此升级脚本只能执行一次；执行前请确认 merchant.settlement_currency 仍存在。
ALTER TABLE merchant DROP COLUMN settlement_currency;
