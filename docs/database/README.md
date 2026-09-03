# 数据库 SQL

本目录是当前后端数据库的完整 SQL 归档。`payment-acquiring-complete.sql` 是唯一的初始化入口，包含表结构、初始化数据和已合并的结构变更。应用启动不依赖 Flyway。

## 执行顺序

新环境优先使用 [`payment-acquiring-complete.sql`](./payment-acquiring-complete.sql)，它会创建四个数据库并按依赖顺序执行 Platform、Trade、Fund 的全部版本 SQL：

```bash
docker exec -i local-mysql mysql -uroot -p < docs/database/payment-acquiring-complete.sql
```

该文件适用于新库初始化；已有数据库不要重复执行包含 `ALTER TABLE` 的内容。生产环境应先备份，并由受控数据库发布流程执行。

`payment-acquiring-complete.sql` 必须始终反映当前最新表结构和初始化数据。每次数据库变更都必须在同一变更中同步更新该文件；对既有库不兼容或不可重复的变更，额外提供一个明确命名的升级脚本。已有环境升级前应由数据库发布流程基于备份和变更审计执行经过评审的 SQL；新环境直接执行完整 SQL。

已有环境升级商户资料字段时，执行 [`merchant-profile-stripe-fields-upgrade.sql`](./merchant-profile-stripe-fields-upgrade.sql)。该脚本要求 MySQL 8.4+，可重复执行。

已有 `pay_platform` 删除商户默认结算币种时，执行 [`merchant-settlement-currency-removal.sql`](./merchant-settlement-currency-removal.sql) 一次。商户结算币种以已绑定产品的产品能力币种为准；该脚本会删除旧列，应在备份后执行。已有 `pay_fund` 可执行 [`fund-ledger-currency-index.sql`](./fund-ledger-currency-index.sql)，为按商户和币种聚合余额增加索引。

已有 `pay_platform` 删除商户支付回调配置时，执行 [`merchant-callback-config-removal.sql`](./merchant-callback-config-removal.sql) 一次。该脚本会删除回调配置表及相关权限数据，应在备份后执行。

若既有 `pay_platform` 缺少后台菜单、权限或角色关联表，使用 [`platform-menu-initialization.sql`](./platform-menu-initialization.sql) 补齐 Platform 菜单/RBAC 基线。该脚本可重复执行，会创建缺失表、同步菜单元数据，并仅为 `ADMIN` 授予全部初始导航；其他角色应由管理员在后台按职责配置菜单授权。

```bash
docker exec -i local-mysql mysql -uroot -p < docs/database/platform-menu-initialization.sql
```

生产环境应使用受控数据库发布 Job、备份和回滚方案执行完整 SQL，并保存执行版本、校验哈希和结果。
