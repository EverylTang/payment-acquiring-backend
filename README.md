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

```bash
java -jar trade-service/target/trade-service-0.1.0-SNAPSHOT.jar
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

当前已实现阶段一和阶段二的最小闭环：订单创建幂等、订单查询/取消、终态保护、模拟路由与费率快照、支付回调状态更新、平台配置快照接口、资金成功入账幂等接口，以及 `infra/mysql/init/10-mvp-schema.sql` 的订单/支付尝试/账务表结构。订单和资金分录已通过 JDBC 写入 MySQL，下一步接入真实渠道适配器、跨服务成功事件和 RocketMQ Outbox。

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
