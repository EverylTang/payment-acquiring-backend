# 数据库 SQL

本目录是当前后端数据库的完整 SQL 归档。`payment-acquiring-complete.sql` 是唯一的初始化入口，包含表结构、初始化数据和已合并的结构变更。应用启动不依赖 Flyway。

## 执行顺序

新环境优先使用 [`payment-acquiring-complete.sql`](./payment-acquiring-complete.sql)，它会创建四个数据库并按依赖顺序执行 Platform、Trade、Fund 的全部版本 SQL：

```bash
docker exec -i local-mysql mysql -uroot -p < docs/database/payment-acquiring-complete.sql
```

该文件适用于新库初始化；已有数据库不要重复执行包含 `ALTER TABLE` 的内容。生产环境应先备份，并由受控数据库发布流程执行。

历史版本已合并进完整 SQL 文件，不再单独维护增量迁移目录。已有环境升级前应由数据库发布流程基于备份和变更审计执行经过评审的 SQL；新环境直接执行完整 SQL。

生产环境应使用受控数据库发布 Job、备份和回滚方案执行完整 SQL，并保存执行版本、校验哈希和结果。
