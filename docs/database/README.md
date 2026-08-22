# 数据库 SQL

本目录是当前后端数据库的完整 SQL 归档，包含表结构、初始化数据和后续结构变更。应用启动不再依赖 Flyway；新环境或版本发布时由数据库发布流程按顺序执行。

## 执行顺序

1. 执行 `00-databases.sql` 创建数据库。
2. 按服务选择对应数据库，使用自然数字顺序执行 `migrations/platform-service/`、`migrations/trade-service/`、`migrations/fund-service/` 中的 SQL；不要使用普通字典序，否则 `V10` 可能早于 `V2` 执行。
3. 文件名中的 `V1`、`V2` 等仅表示执行顺序，不是运行时迁移机制。

`10-mvp-schema.sql` 是早期 MVP 基础表结构归档；完整环境应继续执行服务 SQL。已存在数据库升级时只执行尚未执行的版本文件，不要重复执行包含 `ALTER TABLE` 的版本。

示例（按服务指定数据库，避免依赖 SQL 文件内的当前连接库）：

```bash
for file in $(find docs/database/migrations/platform-service -maxdepth 1 -name 'V*.sql' | sort -V); do
  docker exec -i local-mysql mysql -uroot -p pay_platform < "$file"
done
for file in $(find docs/database/migrations/trade-service -maxdepth 1 -name 'V*.sql' | sort -V); do
  docker exec -i local-mysql mysql -uroot -p pay_trade < "$file"
done
for file in $(find docs/database/migrations/fund-service -maxdepth 1 -name 'V*.sql' | sort -V); do
  docker exec -i local-mysql mysql -uroot -p pay_fund < "$file"
done
```

生产环境应使用受控数据库发布 Job、备份和回滚方案执行这些 SQL，并保存执行版本、校验哈希和结果。
