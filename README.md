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
├── infra/mysql/init/          # 数据库初始化
├── infra/rocketmq/            # Broker 配置
├── docker-compose.yml         # 本地 Broker + MinIO
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

启动前请确认本地 Nacos 已运行，并已在 `payment` namespace、`PAYMENT_GROUP` 中发布公共配置和对应服务的环境配置。各服务代码内的 `application.yml` 仅保留 Nacos 启动引导，数据库、Redis、端口、Flyway、JWT 和路由配置均从 Nacos 加载。

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

## 本地基础设施

该仓库的 Compose 只负责补充 RocketMQ Broker 和 MinIO，默认复用本机已经存在的 `local-dev-network`、MySQL、Redis、Nacos 和 RocketMQ NameServer：

```bash
docker compose up -d
docker ps --filter name=payment-
```

数据库初始化：

宿主机未安装 MySQL 客户端时，可直接通过 Docker 执行初始化脚本：

```bash
docker exec -i local-mysql mysql -uroot -p < infra/mysql/init/00-databases.sql
docker exec -i local-mysql mysql -uroot -p < infra/mysql/init/10-mvp-schema.sql
```

脚本会创建 `pay_platform`、`pay_trade`、`pay_fund` 和 `pay_audit` 数据库，以及 MVP 所需的订单、支付尝试和账务分录表。交易服务使用 `pay_trade.payment_order` 真实落库，资金服务使用 `pay_fund.ledger_entry` 真实落库。

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
- Platform 商户、产品、渠道、路由、费率、风控配置落库，以及 Flyway V1-V3 自动迁移。
- 配置草稿、审核、批准、发布基础流程和已发布配置快照。
- Trade 订单创建幂等、查询、取消、终态保护、模拟回调和 MySQL 落库。
- Fund 支付成功幂等入账和 MySQL 落库。
- 所有服务业务配置迁移到 Nacos，代码中的 `application.yml` 只保留启动引导。

当前已补充：Payment Attempt 查询与回调安全基础、Trade 到 Fund 的 Outbox/RocketMQ 可靠事件链路、退款执行/重试/回调幂等、Fund 退款冲正、账单导入和差异处置基础接口、运营后台 DLQ/对账页面。

当前已在本地真实 MySQL/RocketMQ 完成支付成功 E2E（Trade Outbox -> RocketMQ -> Fund ledger），Fund Flyway 已执行至 V6，退款 CAS/事件消费幂等、逐笔对账差异分类、Prometheus/OpenTelemetry 配置和 Testcontainers 验收测试已补齐。后端 `mvn test`、前端 `npm run build` 均通过；构建产物和系统元数据不纳入源码。仍需接入具体供应商退款协议，并在 CI/生产环境执行 `RUN_TESTCONTAINERS=true`、DLQ 故障注入、Broker/Trade/Fund 重启恢复和告警联调。

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
