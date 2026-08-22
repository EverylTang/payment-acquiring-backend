# payment-acquiring-backend

通用收单系统后端独立 Git 仓库，面向后端开发、CI 构建和本地基础设施运行。

## 技术栈

- Java 21
- Spring Boot 3.3
- Maven 多模块
- MySQL 8.4
- Redis 7.2
- Nacos 2.x
- RocketMQ 5.2
- MinIO

## 目录

```text
payment-acquiring-backend/
├── pom.xml
├── gateway-service/           # 8080
├── platform-service/          # 8081
├── trade-service/             # 8082
├── fund-service/              # 8083
├── docs/database/             # 完整数据库表结构、初始化数据和版本 SQL
└── .gitignore
```

## 构建

```bash
mvn clean package -DskipTests
```

单独构建交易服务：

```bash
mvn -pl trade-service -am package -DskipTests
```

## 运行

启动前请确认本地 Nacos 已运行，并已在 `payment` namespace、`PAYMENT_GROUP` 中发布公共配置和对应服务的环境配置。各服务代码内的 `application.yml` 仅保留 Nacos 启动引导，数据库、Redis、端口、JWT 和路由配置均从 Nacos 加载；数据库结构和数据由 `docs/database/` 的发布 SQL 管理。

Platform 首次初始化管理员时，可在 Nacos 的 `platform-service-dev.yml` 中临时开启 Bootstrap；已有管理员后应关闭该开关，并通过安全方式轮换初始密码。

```bash
java -jar gateway-service/target/gateway-service-0.1.0-SNAPSHOT.jar
java -jar platform-service/target/platform-service-0.1.0-SNAPSHOT.jar
java -jar trade-service/target/trade-service-0.1.0-SNAPSHOT.jar
java -jar fund-service/target/fund-service-0.1.0-SNAPSHOT.jar
```

健康检查：

```bash
curl http://127.0.0.1:8082/actuator/health
curl http://127.0.0.1:8082/api/v1/payments/orders/health
```

Prometheus 指标：

Trade 和 Fund 暴露 Spring Boot Actuator Prometheus 端点：

```text
http://127.0.0.1:8082/actuator/prometheus
http://127.0.0.1:8083/actuator/prometheus
```

Prometheus 抓取配置应由部署环境维护，示例：

```yaml
scrape_configs:
  - job_name: payment-trade
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["trade-service:8082"]
  - job_name: payment-fund
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["fund-service:8083"]
```

告警规则由外部 Prometheus/Alertmanager 部署流程维护，覆盖退款 DEAD、退款冲正失败和对账差异。仓库不携带监控容器或告警规则文件。OpenTelemetry OTLP 地址通过 `OTEL_EXPORTER_OTLP_ENDPOINT` 配置，默认指向本机 `4318` 端口。

## 本地基础设施

所有依赖服务由本地 Docker 或基础设施平台独立维护，应用仓库不再提供 Compose 启动文件。启动服务前请确认 MySQL、Redis、Nacos、RocketMQ NameServer、RocketMQ Broker 和 MinIO 已运行，并接入同一网络或开放对应端口。

数据库初始化：

宿主机未安装 MySQL 客户端时，可直接通过 Docker 执行初始化脚本：

```bash
docker exec -i local-mysql mysql -uroot -p < docs/database/payment-acquiring-complete.sql
```

完整数据库 SQL（包括表结构、初始化数据和版本变更）位于 [`docs/database/payment-acquiring-complete.sql`](docs/database/payment-acquiring-complete.sql)，执行说明见 [`docs/database/README.md`](docs/database/README.md)。该入口会创建 `pay_platform`、`pay_trade`、`pay_fund` 和 `pay_audit` 数据库，并按服务 SQL 创建完整表结构和初始化数据。交易服务使用 `pay_trade.payment_order` 真实落库，资金服务使用 `pay_fund.ledger_entry` 真实落库。

后端数据持久层统一使用 MyBatis-Plus，禁止新增 Spring JDBC/JdbcClient/JdbcTemplate 或直接 JDBC 访问；详细约定见 [`docs/persistence-guidelines.md`](docs/persistence-guidelines.md)。

Redis 和 MySQL 的连接密码由 Nacos 配置中心统一管理，不再通过项目根目录的环境变量文件或环境变量配置。

不要提交真实 `.env`、数据库密码、渠道密钥或商户 API Key。

## Git 上传

```bash
git init
git add .
git commit -m "chore: initialize payment acquiring backend"
git branch -M main
git remote add origin <backend-repository-url>
git push -u origin main
```

## 当前边界

当前已实现：

- Gateway 基础路由、请求 ID、内部 Token 和后台 JWT 校验。
- Platform 管理员登录、BCrypt、JWT、RBAC 基础能力和管理员 Bootstrap。
- Platform 商户、产品、渠道、路由、费率、风控配置落库，以及数据库版本 SQL V1-V3。
- 配置草稿、审核、批准、发布基础流程和已发布配置快照。
- Trade 订单创建幂等、查询、取消、终态保护、模拟回调和 MySQL 落库。
- Fund 支付成功幂等入账和 MySQL 落库。
- 所有服务业务配置迁移到 Nacos，代码中的 `application.yml` 只保留启动引导。

当前已补充：Payment Attempt 查询与回调安全基础、Trade 到 Fund 的 Outbox/RocketMQ 可靠事件链路、退款执行/重试/回调幂等、Fund 退款冲正、账单导入和差异处置基础接口、运营后台 DLQ/对账页面。

当前已在本地真实 MySQL/RocketMQ 完成支付成功 E2E（Trade Outbox -> RocketMQ -> Fund ledger），Fund 数据库已执行至 V6，退款 CAS/事件消费幂等、逐笔对账差异分类、Prometheus/OpenTelemetry 配置已补齐。后端 `mvn test`、前端 `npm run build` 均通过；构建产物和系统元数据不纳入源码。仍需接入具体供应商退款协议，并在 CI/生产环境执行 SQL 发布、DLQ 故障注入、Broker/Trade/Fund 重启恢复和告警联调。

未完成项按优先级：

- P0：RocketMQ 实际重试/DLQ/人工重放、Broker 与服务重启恢复、Outbox/Attempt 多实例真实 MySQL 验证、管理接口安全测试、迁移 Job 和 CI 集成验收。
- P1：真实支付/退款渠道协议、标准 HMAC 与防重放、模拟渠道持久化、订单与 Attempt 事务拆分、账单文件/MinIO 导入、完整差异处置审批、配置差异与回滚、完整菜单按钮和数据权限、稳定事件 DTO 与链路字段透传。
- P2：Grafana/告警平台和 OTel Collector 联调、业务指标生产验证、限流熔断、商户签名认证、密钥轮换、Nacos 权限隔离、容量与故障注入测试。

创建订单示例：

```bash
curl -X POST http://127.0.0.1:8082/api/v1/payments/orders \\
  -H 'Content-Type: application/json' \\
  -H 'Idempotency-Key: demo-001' \\
  -d '{"merchantId":"merchant-demo","merchantOrderNo":"order-001","productCode":"default-pay","paymentMethod":"CARD","country":"US","currency":"USD","amount":100.00}'
```

查询订单和模拟回调：

```bash
curl http://127.0.0.1:8082/api/v1/payments/orders/{orderId}
curl -X POST 'http://127.0.0.1:8082/api/v1/payments/orders/{orderId}/callback?status=SUCCESS'
```

资金成功入账示例：

```bash
curl -X POST http://127.0.0.1:8083/api/internal/v1/ledger/payment-success \\
  -H 'Content-Type: application/json' \\
  -d '{"idempotencyKey":"ledger-demo-001","orderId":"{orderId}","merchantId":"merchant-demo","currency":"USD","amount":100.00}'
```
