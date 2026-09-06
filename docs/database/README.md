# 数据库 SQL

本目录只保留当前后端数据库的完整初始化脚本。`payment-acquiring-complete.sql` 是唯一 SQL 入口，包含表结构、初始化数据、历史兼容变更、产品字段、国家/币种基础数据、菜单和权限。应用启动不依赖 Flyway。

## 执行顺序

新环境使用 [`payment-acquiring-complete.sql`](./payment-acquiring-complete.sql)，它会创建四个数据库并按依赖顺序创建 Platform、Trade、Fund 的完整表结构和初始化数据：

```bash
docker exec -i local-mysql mysql -uroot -p < docs/database/payment-acquiring-complete.sql
```

该文件适用于新库初始化；已有数据库不要重复执行包含 `ALTER TABLE` 的内容。生产环境应先备份，并由受控数据库发布流程执行。

新环境初始化后，`pay_platform` 默认包含：

- 逻辑产品的产品类型、收款接入模式、默认国家/币种、描述和账单描述符。
- `country_master` 国家/地区基础数据、`currency_master` 币种基础数据，以及 `country_currency_master` 国家/币种可用组合。
- 美国、中国、英国、新加坡、中国香港等常用国家/地区，以及 USD、CNY、GBP、SGD、HKD、JPY 等常用币种及其初始可用组合。
- “国家与币种”后台菜单、`master-data:*` 权限和 ADMIN/OPS 的初始授权。

`payment-acquiring-complete.sql` 必须始终反映当前最新表结构、索引、菜单权限、初始化数据和兼容变更。每次数据库变更都必须在同一变更中同步更新该文件；本目录不维护拆分的升级、索引或菜单初始化脚本。已有环境升级前应由数据库发布流程基于备份和变更审计执行经过评审的 SQL；新环境直接执行完整 SQL。

## 产品与基础数据

产品管理字段、国家/地区、币种、国家/币种关联及其菜单权限已全部并入 `payment-acquiring-complete.sql`；不再维护独立的产品管理或主数据初始化脚本。产品默认市场和支付能力仅能使用国家、币种及其关联均已启用的组合。

已有生产库不能直接重跑完整初始化 SQL。涉及产品字段、主数据表或废弃 `country_master.iso3_code` 列的历史环境，应基于备份、现有表结构和发布审计，由受控数据库发布流程生成并评审专用变更；删除 `iso3_code` 属于不可恢复操作。

生产环境应使用受控数据库发布 Job、备份和回滚方案执行完整 SQL，并保存执行版本、校验哈希和结果。

## 商户结算

支付成功事件会为已启用收款的商户创建结算明细。上线或启用商户产品前，必须为每个商户/币种配置一条当前生效的 `ACTIVE` 结算规则；缺少规则的事件会保留为失败状态，待规则补齐后可通过既有事件重放流程恢复。默认每日 01:00 UTC 处理到期结算，支持通过 `FUND_SETTLEMENT_CRON` 和 `FUND_SETTLEMENT_ZONE` 调整。
