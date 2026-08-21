# 支付收单系统项目状态与开发计划

> 更新时间：2026-08-21
>
> 适用仓库：`payment-acquiring-backend`
>
> 本文以当前代码为准，记录已实现能力、第二阶段验收差距、收尾任务和第三阶段开发路线。Nacos 中的真实密码、渠道密钥、商户 API Key 等敏感信息不写入本文。

## 1. 当前结论

项目已完成平台配置、交易订单和可靠事件链路的主体代码，当前主链路为：

```text
管理员认证与配置发布
→ Gateway 鉴权与路由
→ Trade 创建订单和 Payment Attempt
→ 模拟渠道执行、查询、取消和签名回调
→ 订单与 Attempt 状态协调
→ PAYMENT_SUCCEEDED 同事务写入 Outbox
→ RocketMQ 至少一次投递
→ Fund 幂等写入 ledger_entry
```

截至 2026-08-21，第二阶段代码实现完成度约为 `88%～92%`，整体验收完成度约为 `72%～76%`。业务主链路、核心并发控制、运维补偿基础和事件版本校验已落地，但真实基础设施、并发集成和重启恢复验收尚未闭环，因此当前结论是：

- Attempt CAS、Outbox 抢占与锁恢复、有限重试与 DEAD、Fund 消费记录和统一账务服务已完成核心实现。
- Outbox 管理接口已具备查询、redrive、操作审计、Gateway 内部凭证和 ADMIN/OPS 角色保护；claim token 已完成代码实现，真实数据库验收仍未完成。
- Fund 消费者已具备 processing lease、失败记录恢复、RocketMQ 重试参数和 schemaVersion 校验；失败记录查询、冲突分类、人工重放和重放审计已具备代码基础，真实 RocketMQ/DLQ 验收仍未完成。
- P1 Attempt 查询调度已完成基础实现；模拟渠道状态持久化、HMAC 防重放和请求链路 ID 透传仍未完成；事件已增加版本及元数据字段，但稳定公共 DTO 尚未抽取。
- 第二阶段核心代码收尾已基本完成，下一阶段重点是通过真实 MySQL/RocketMQ、故障恢复和权限边界验收；验收通过后再正式进入第三阶段退款与对账开发。

Admin 初始密码不保存在仓库中，由 Nacos 或外部环境配置注入。仓库默认密码为空，不能据此登录；应在目标环境的 `payment` namespace、`PAYMENT_GROUP` 分组和对应 `platform-service-dev.yml` 配置中核实，且不得将真实密码写入本文、Git 或日志。

## 2. 系统与基础设施

### 2.1 服务职责

| 服务 | 默认端口 | 当前职责 | 状态 |
| --- | ---: | --- | --- |
| `gateway-service` | 8080 | 统一入口、请求头清洗、内部接口保护、请求 ID 透传和服务路由 | 基础能力已完成 |
| `platform-service` | 8081 | 管理员认证、RBAC、商户/产品/渠道配置、规则发布、配置快照 | 配置域主体已完成 |
| `trade-service` | 8082 | 订单、Payment Attempt、模拟渠道、回调、Outbox 和消息发布 | 第二阶段主体已完成 |
| `fund-service` | 8083 | 支付成功入账、HTTP/MQ 幂等写入 | 第二阶段主体已完成 |

### 2.2 基础设施

本地环境使用：

- MySQL 8.4
- Redis 7.2
- Nacos 2.3.2
- RocketMQ 5.2.0
- MinIO

仓库 `docker-compose.yml` 管理 RocketMQ Broker 和 MinIO，并复用外部 Docker 网络 `local-dev-network`。RocketMQ Broker 当前映射端口：

```text
10909
10911
```

本地服务运行在宿主机时，Broker 宣告地址使用 `127.0.0.1`。若后续将 Trade/Fund 容器化，必须按环境改为容器可访问的 DNS 或内网地址，不能继续使用容器自身的 `127.0.0.1`。

### 2.3 Nacos 配置约定

统一配置约定：

| 配置项 | 默认值 | 覆盖方式 |
| --- | --- | --- |
| Nacos 地址 | `127.0.0.1:8848` | `NACOS_ADDR` |
| namespace | `payment` | `NACOS_NAMESPACE` |
| group | `PAYMENT_GROUP` | `NACOS_GROUP` |
| 环境 | `dev` | `spring.profiles.active` |
| 格式 | `yml` | 固定配置 |

每个服务导入：

```text
application.yml
{service-name}-{profile}.yml
```

Trade/Fund 本地开发配置已包含：

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
```

Trade 已配置存量数据库 Flyway 基线。Trade 新增 `V4__payment_outbox_claim_and_dead_letter.sql`、`V5__payment_outbox_operation_audit.sql`、`V6__payment_outbox_claim_token.sql` 和 `V7__payment_attempt_query_schedule.sql`；Fund 新增 `V1__fund_ledger_baseline.sql`、`V2__payment_event_consumption.sql`、`V3__payment_event_consumption_claim.sql` 和 `V4__payment_event_replay_audit.sql`。Fund 当前通过 `FUND_FLYWAY_ENABLED:false` 默认关闭；在真实 MySQL 验证基线和初始化流程前，不应宣称 Fund 已完成正式迁移接管。

代码使用 `optional:nacos` 导入配置。生产环境需增加必要配置校验，避免 Nacos 缺失时使用不安全默认值启动。

## 3. 第一阶段与平台基础能力

### 3.1 Gateway

已实现：

- Spring Cloud Gateway 统一入口。
- 内部接口 Token 校验。
- 清理 Hop-by-hop 和客户端伪造身份请求头。
- 生成或透传合法 `X-Request-Id`。
- Platform、Trade 和 Fund 基础路由。

待完善：

- 外部商户签名认证和防重放。
- 限流、熔断和服务身份认证。
- 生产级审计和链路追踪。

### 3.2 Platform

已实现：

- 管理员登录和身份查询。
- JWT、RBAC 基础能力。
- 商户、逻辑产品、产品能力、商户产品绑定。
- 渠道、渠道能力、路由、费率、风控配置。
- 配置草稿、审核、批准、发布和快照查询。
- 发布前规则完整性、金额区间、费率和冲突校验。
- MySQL 持久化和 Flyway 迁移。

待完善：

- 配置版本差异。
- 正式回滚和严格审批约束。
- 真实渠道网络健康探测。
- 完整菜单、按钮和数据权限。

## 4. 第二阶段已实现能力

## 4.1 订单与 Payment Attempt 状态机

订单状态：

```text
CREATED → PAYING → SUCCESS / FAILED / UNKNOWN / CANCELED
UNKNOWN → PAYING / SUCCESS / FAILED / CANCELED
```

`SUCCESS`、`FAILED`、`CANCELED` 为终态，终态不能被后续状态覆盖。

Payment Attempt 状态：

```text
CREATED → PROCESSING → SUCCESS / FAILED / TIMEOUT / CANCELED / UNKNOWN
UNKNOWN → PROCESSING / SUCCESS / FAILED / TIMEOUT / CANCELED
```

已实现：

- Attempt 创建、查询、取消和重试。
- Attempt 序号、渠道请求号和请求/响应快照。
- 条件状态更新和数据库唯一约束。
- Attempt 状态协调订单状态。
- 状态机纯领域测试。

核心文件：

- `trade-service/src/main/java/com/example/payments/trade/service/domain/OrderStatus.java`
- `trade-service/src/main/java/com/example/payments/trade/service/domain/PaymentAttemptStatus.java`
- `trade-service/src/main/java/com/example/payments/trade/service/application/PaymentAttemptService.java`
- `trade-service/src/main/resources/db/migration/V1__payment_attempt_lifecycle.sql`

### 4.2 模拟渠道与回调

统一渠道适配器支持：

- 创建支付。
- 查询支付。
- 取消支付。
- 回调验签。

模拟渠道支持 `SUCCESS`、`FAILED`、`PROCESSING` 和 `TIMEOUT` 行为。回调原文、签名和处理结果会落库，并通过 `callback_id` 唯一键防止重复处理。

核心文件：

- `trade-service/src/main/java/com/example/payments/trade/service/application/PaymentChannelAdapter.java`
- `trade-service/src/main/java/com/example/payments/trade/service/application/SimulatedChannelAdapter.java`
- `trade-service/src/main/resources/db/migration/V2__callback_deduplication.sql`

### 4.3 Trade Outbox

Attempt 成功后，在同一事务调用路径内完成：

1. 条件更新 Attempt。
2. 协调订单为 `SUCCESS`。
3. 使用 Jackson 序列化 `PAYMENT_SUCCEEDED`。
4. 写入 `payment_outbox_event`。

事件唯一键：

```text
PAYMENT_SUCCEEDED:{orderId}:{attemptId}
```

Outbox 记录包含：

- `event_id`
- 聚合类型和聚合 ID
- 事件类型和 JSON payload
- 发布状态
- 发布尝试次数
- 下次重试时间
- 最后错误
- 创建和发布时间
- `PROCESSING` claim 锁、锁持有者和过期时间
- 首次失败时间、失败类型和 `DEAD` 时间

发布器具备多实例条件 claim、过期锁恢复、最大尝试次数、指数退避和 `DEAD` 状态；已新增管理员查询、人工 redrive 和操作审计基础接口，并增加 Gateway 内部凭证、ADMIN/OPS 角色保护和独立 claim token；仍需真实 MySQL 多实例验收。

核心文件：

- `trade-service/src/main/java/com/example/payments/trade/service/infrastructure/persistence/PaymentOutboxEventRepository.java`
- `trade-service/src/main/resources/mapper/PaymentOutboxEventMapper.xml`
- `trade-service/src/main/resources/db/migration/V3__payment_outbox.sql`
- `trade-service/src/main/resources/db/migration/V4__payment_outbox_claim_and_dead_letter.sql`
- `trade-service/src/main/resources/db/migration/V5__payment_outbox_operation_audit.sql`
- `trade-service/src/main/resources/db/migration/V6__payment_outbox_claim_token.sql`
- `trade-service/src/main/resources/db/migration/V7__payment_attempt_query_schedule.sql`
- `trade-service/src/main/java/com/example/payments/trade/service/interfaces/rest/AdminOutboxController.java`

### 4.4 RocketMQ 发布

`PaymentOutboxPublisher` 默认每 5 秒扫描最多 50 条 `PENDING` 或 `RETRYING` 事件：

- 发送成功后标记 `PUBLISHED`。
- 发送失败后增加尝试次数并延迟重试。
- 采用至少一次投递语义。
- 发送成功但状态更新失败时允许重复发布，由 Fund 幂等兜底。

Topic：

```text
PAYMENT_SUCCEEDED
```

核心文件：

- `trade-service/src/main/java/com/example/payments/trade/service/application/PaymentOutboxPublisher.java`

### 4.5 Fund 消费与账务幂等

Fund 已新增 `payment_event_consumption` 消费记录，并通过 `LedgerEntryApplicationService` 统一 HTTP 与 MQ 入账。消费记录保存事件标识、原始 payload、SHA-256 摘要、处理状态、次数、错误和关联分录；重复事件会校验 payload，重复幂等键会核对订单、商户、金额、币种和分录字段，字段冲突不会被当作成功。

Fund 已新增失败消费记录查询、失败类型分类、人工重放和重放审计接口；CONFLICT 记录不会被无条件重放，重放使用保存的原始 payload 重新投递。仍需真实 RocketMQ DLQ 和重放恢复验收；消费者已具备 FAILED 状态记录、processing lease 恢复、显式最大重试次数和 schemaVersion 校验。

Fund 消费组：

```text
fund-payment-success
```

收到 `PAYMENT_SUCCEEDED` 后创建：

```text
entry_type = PAYMENT_SUCCESS
debit_credit = CREDIT
idempotency_key = payment-success:{orderId}
```

`ledger_entry.idempotency_key` 有数据库唯一约束，重复消息不会重复入账。原有 HTTP 内部入账接口仍保留。

核心文件：

- `fund-service/src/main/java/com/example/payments/fund/service/application/PaymentSuccessEventConsumer.java`
- `fund-service/src/main/java/com/example/payments/fund/service/interfaces/rest/LedgerController.java`
- `fund-service/src/main/java/com/example/payments/fund/service/application/LedgerEntryApplicationService.java`
- `fund-service/src/main/java/com/example/payments/fund/service/infrastructure/persistence/PaymentEventConsumptionMapper.java`
- `fund-service/src/main/resources/mapper/LedgerEntryMapper.xml`
- `fund-service/src/main/resources/mapper/PaymentEventConsumptionMapper.xml`
- `fund-service/src/main/resources/db/migration/V1__fund_ledger_baseline.sql`
- `fund-service/src/main/resources/db/migration/V2__payment_event_consumption.sql`
- `fund-service/src/main/resources/db/migration/V3__payment_event_consumption_claim.sql`
- `fund-service/src/main/resources/db/migration/V4__payment_event_replay_audit.sql`
- `fund-service/src/main/java/com/example/payments/fund/service/application/PaymentEventReplayAdminService.java`
- `fund-service/src/main/java/com/example/payments/fund/service/interfaces/rest/AdminPaymentEventController.java`

### 4.6 当前自动化测试

当前测试覆盖：

- 订单终态保护。
- Attempt 状态迁移。
- 模拟渠道行为。
- Outbox 发布成功、失败重试、claim、claim token 和 DEAD 分支。
- Fund 正常消费、重复键幂等、payload 冲突、非法消息、处理失败和未知事件版本拒绝。
- Gateway、Trade 和 Fund 管理接口的代码级内部凭证与角色保护已完成，独立安全测试仍待补充。

最近一次执行：

```bash
mvn test
git diff --check
```

结果：

```text
所有 Maven 模块 BUILD SUCCESS
Gateway: 0 tests（当前模块未配置测试用例）
Trade: 10 tests passed
Fund: 4 tests passed
git diff --check passed
```

这些测试主要是纯领域或 Mockito 单元测试，尚不能替代真实 MySQL 和 RocketMQ 端到端验收。

当前仍未执行：

- Testcontainers MySQL/RocketMQ 集成测试。
- Broker 停止/恢复和 Trade/Fund 重启恢复验收。
- RocketMQ 实际最大重试次数、DLQ 和人工重放验收。

## 5. 第二阶段剩余验收与收尾项

代码层面的 P0/P1 主体已基本完成，以下事项主要是测试补充、真实基础设施验证、安全边界验证和仍未实现的增强能力。

## 5.1 P0：进入第三阶段前必须完成

### 5.1.1 Attempt 并发协调验收

核心修复已完成：`PaymentAttemptService.applyResult` 现在使用状态加版本号 CAS，只有更新成功才协调订单；CAS 失败时重新读取数据库真实 Attempt 状态。剩余工作是增加真实并发测试，并明确取消后晚到成功回调的处置策略。Attempt 查询调度已增加查询次数、下次查询时间、退避、过期锁接管和超限转 `TIMEOUT` 的基础实现，仍需补充调度任务单元/集成测试。

验收要求：

1. 查询、取消、回调并发时只允许一个状态转换成功。
2. CAS 失败线程不得使用竞争线程构造的结果协调订单。
3. 取消后晚到成功回调必须有明确且可审计的处理结果。

### 5.1.2 Outbox 管理与多实例验收

多实例原子 claim、`PROCESSING` 锁、过期锁恢复、最大重试、指数退避和 `DEAD` 状态的核心实现已完成，管理员查询、redrive 和操作审计基础接口已加入。剩余工作集中在真实 MySQL 多实例验证和访问边界：

- `GET /api/admin/v1/outbox/dead`
- `GET /api/admin/v1/outbox/{eventId}`
- `POST /api/admin/v1/outbox/{eventId}/redrive`
- Trade 服务端独立认证、管理员 RBAC 和 Gateway 内部信任校验已加入代码，仍需独立安全测试和生产网络隔离验证
- claim token 防止过期 worker 更新新 worker 持有的事件已加入代码，仍需真实 MySQL 多实例验证

### 5.1.3 Outbox 有限重试、死信和人工重发

有限重试、指数退避、最大退避时间、`DEAD` 终态、首次失败、失败类型、错误截断、死信查询、人工 redrive 和审计写入已完成基础实现。仍需补充真实权限校验、并发 redrive 测试，并用真实数据库验证重试边界和锁恢复。

### 5.1.4 Fund 消费记录与差异校验

消费记录、payload 摘要、处理状态、账务关联和幂等字段冲突校验的核心实现已完成。仍需验证：

- 消费失败时记录 `FAILED` 和错误信息。
- `PROCESSING` 记录在消费者重启后可恢复。
- 并发重复消息不会产生重复消费记录或账务分录。
- 真实 MySQL 唯一约束和事务行为符合预期。

### 5.1.5 RocketMQ 重试与 DLQ 处置

当前消费者显式配置最大重试次数、并发集群消费和消费超时，并通过抛出异常触发 RocketMQ 重试。Fund 已提供失败消费记录查询、原始 payload 重放、CONFLICT 分类保护和重放审计；仍需完成 DLQ 运营和真实 Broker 验证。仍需验证：

- 最大消费重试次数。
- 消费失败重试策略。
- DLQ Topic 和告警。
- 真实 DLQ 查询与人工重放链路。
- 重放幂等和操作审计在 Broker 异常时的恢复行为。

### 5.1.6 管理接口访问边界

Trade、Fund 管理接口已增加 Gateway 内部 Token、用户身份和 ADMIN/OPS 角色校验，Gateway 会清理客户端传入的身份头并注入内部 Token。仍需验证：

- 未配置 Token 时管理接口默认拒绝。
- 客户端伪造 `X-Gateway-Token`、`X-User-Id` 和 `X-Roles` 不能绕过校验。
- 非 ADMIN/OPS 角色被拒绝。
- 生产环境禁止直接公开 Trade/Fund 管理端口。

### 5.1.7 真实端到端验收

至少自动验证：

1. 创建订单并获得成功 Attempt。
2. 订单、Attempt 和 Outbox 正确落库。
3. Broker 暂停时 Outbox 进入重试。
4. Broker 恢复后事件发布成功。
5. Fund 消费并只创建一条账务分录。
6. 重复发送同一消息不重复入账。
7. Fund 消费失败后进入重试和 DLQ。
8. 人工重放后成功入账。
9. Trade/Fund 重启后未完成事件自动恢复。

建议增加 Testcontainers 或独立 Docker 验收脚本，并纳入 CI。

## 5.2 P1：第二阶段收尾建议完成

### 5.2.1 模拟渠道状态持久化

当前模拟渠道主要根据字符串推导查询结果，不能稳定模拟长期处理中、处理后失败、取消后晚到成功和网络异常。应保存模拟渠道订单及状态，提供仅开发/测试环境开放的控制接口或测试夹具。

### 5.2.2 Attempt 超时扫描增强

Attempt 查询调度基础实现已完成，包含：

- `query_count`
- `next_query_at`
- `max_query_count`
- 指数退避
- 多实例 claim 和过期锁接管
- 达到阈值后转 `TIMEOUT`

剩余工作是补充任务调度单元测试、真实 MySQL SQL 验证，并拆分外部渠道调用与数据库事务边界。

### 5.2.3 订单和 Attempt 创建协调

订单先转 `PAYING`，随后再创建 Attempt，两者不是一个协调事务。若渠道调用或 Attempt 写入失败，订单可能停留在 `PAYING` 且没有有效 Attempt。

真实渠道接入前应拆分：

1. 本地创建 Attempt。
2. 事务提交。
3. 调用外部渠道。
4. 条件更新 Attempt 和订单。
5. 失败时保留可恢复状态。

避免在数据库事务内持有长时间外部网络调用。

### 5.2.4 回调签名与防重放增强

模拟签名目前不是标准 HMAC，且没有时间戳、nonce 和有效时间窗口。真实渠道适配器必须按渠道协议实现标准签名，并绑定渠道身份、时间戳和防重放策略。

### 5.2.5 统一 Fund 入账应用服务

已完成。HTTP 接口和 MQ 消费者均调用 `LedgerEntryApplicationService`，统一参数、entry ID、幂等键和账务字段一致性校验。剩余工作仅为真实数据库事务和冲突场景验收。

### 5.2.6 事件契约版本化

`PAYMENT_SUCCEEDED` 已增加：

```text
schemaVersion
occurredAt
producer
requestId
traceId
aggregateVersion
```

Fund 已拒绝未知 schemaVersion 并增加单元测试。剩余工作是将事件契约提取为稳定 DTO、增加完整序列化兼容测试、从请求上下文透传 requestId/traceId，并明确当前 aggregateVersion 表示 Attempt 版本。

### 5.2.7 Flyway 治理

迁移文件已补齐，但 Fund 仍通过 `FUND_FLYWAY_ENABLED:false` 默认关闭，正式治理尚未完成：

- 用真实 MySQL 验证已有 `ledger_entry` 与 Fund 基线迁移的一致性。
- 明确开发/测试环境自动迁移和生产环境独立迁移 Job。
- 确认初始化脚本不再与 Flyway 重复管理同一张表。
- 验证新环境从空库到可运行服务的完整迁移流程。

## 6. 第二阶段验收与收尾计划

### Sprint 2.1：并发正确性与发布可靠性

优先级：P0

已完成：

1. `applyResult` 条件更新结果处理和 Attempt 版本 CAS。
2. Outbox 原子 claim、锁恢复、最大重试、指数退避和 `DEAD` 状态。
3. Outbox 查询、人工 redrive、操作审计和 claim token。

待验收：

1. 增加 Attempt 并发状态测试。
2. 用真实 MySQL 验证多实例 claim、锁过期和 token 失效边界。

完成标准：

- 多实例不会同时持有同一待发布事件。
- Attempt 实际状态、订单状态和 Outbox 事件保持一致。
- 发布失败达到阈值后停止自动重试并可人工恢复。

### Sprint 2.2：消费可靠性与人工补偿

优先级：P0

已完成：

1. Fund 消费记录表、processing lease 和 Flyway 迁移。
2. 统一账务应用服务和重复消息字段一致性核对。
3. RocketMQ 重试参数、失败分类、失败记录查询、人工重放、RBAC 和审计基础。

待验收：

1. 真实 Broker 的 DLQ、重放和幂等恢复链路。
2. Fund 管理接口安全测试和生产端口隔离。

完成标准：

- 每个事件的接收、处理、幂等、失败和重放状态可查询。
- 重复事件不会重复入账。
- 相同幂等键但金额、币种或商户不一致时生成差异并告警。

### Sprint 2.3：端到端验收与环境治理

当前为下一执行重点。

优先级：P0

1. 增加 MySQL、RocketMQ 真实集成测试。
2. 验证 Broker 停止、恢复和 Trade/Fund 服务重启。
3. 将端到端验收纳入 CI。
4. 完成 Trade/Fund Flyway 基线和新环境初始化。
5. 更新 README、本地启动和故障恢复手册。
6. 补充 Gateway、Trade、Fund 管理接口安全测试。

完成标准：

- 第二阶段端到端验收用例全部自动通过。
- 新环境可通过标准步骤完成建库、迁移、启动和验收。
- 失败消息具备可观测、可告警、可重放、可审计能力。

## 7. 第三阶段开发计划

第二阶段 P0 收尾并通过验收后，第三阶段定位为：

```text
退款与资金冲正
→ 渠道账单与日对账
→ 差异处置与运营闭环
```

## 7.1 P0：退款闭环

### 7.1.1 退款领域模型

新增：

- `refund_order`
- `refund_attempt`
- 退款状态机
- 退款幂等键
- 累计退款金额和可退款金额

支持：

- 全额退款。
- 部分退款。
- 多次部分退款。
- 并发退款金额控制。
- 失败、处理中、未知和成功状态恢复。

### 7.1.2 渠道退款能力

扩展渠道适配器：

- 发起退款。
- 查询退款。
- 取消退款，仅在渠道支持时开放。
- 退款回调验签和去重。
- 退款超时查询和重试。

### 7.1.3 退款可靠事件

退款成功后同事务写入：

```text
REFUND_SUCCEEDED
```

复用第二阶段完成的 Outbox、RocketMQ、消费记录、死信和人工补偿框架。

### 7.1.4 Fund 冲正

Fund 消费退款成功事件后：

- 创建 `REFUND` 或 `REVERSAL` 借记分录。
- 使用 `reversal_of` 关联原支付分录。
- 保证退款事件幂等。
- 校验累计冲正金额不能超过原支付金额。
- 支持支付成功但退款冲正失败的差异告警和人工补偿。

退款阶段完成标准：

- 全额、部分和多次部分退款均可正确执行。
- 并发退款不会超过可退款金额。
- 重复回调和重复消息不会重复冲正。
- 退款订单、渠道 Attempt、事件和资金分录可完整追踪。

## 7.2 P1：渠道对账与差异处理

### 7.2.1 渠道账单

- 支持渠道账单上传或定时下载。
- 原始账单存储到 MinIO。
- 保存文件哈希、账单日期、渠道、版本和导入状态。
- 重复文件不重复导入。

### 7.2.2 日对账

按以下维度匹配：

- 平台订单号。
- 渠道订单号。
- 商户。
- 金额和币种。
- 支付或退款状态。

差异类型：

```text
MATCHED
CHANNEL_ONLY
PLATFORM_ONLY
AMOUNT_MISMATCH
CURRENCY_MISMATCH
STATUS_MISMATCH
DUPLICATE
```

### 7.2.3 差异处置

- 差异单认领、备注、复核和关闭。
- 补单、冲正和忽略操作需要权限、原因和二次确认。
- 高风险资金操作进入审批流程。
- 所有操作写入审计日志。

## 7.3 P1：运营后台

新增或完善页面：

- 订单详情时间线。
- Payment Attempt 和回调原文。
- Outbox、消费记录和 DLQ。
- 人工重发和重放。
- 退款申请、审核和进度。
- 账务分录。
- 渠道账单和对账差异。

后台不得直接修改订单或账务表，所有处置必须调用领域服务接口。

## 7.4 P2：可观测性和生产化

指标：

- Outbox 待发布数和最老消息年龄。
- 发布重试数和 DEAD 数量。
- RocketMQ 消费堆积、失败和 DLQ 数量。
- 支付成功但未入账数量。
- Attempt 超时率和查询恢复率。
- 退款成功但未冲正数量。
- 对账差异数量和处理时长。

生产化任务：

- Prometheus 和 Grafana。
- OpenTelemetry 链路追踪。
- requestId、eventId、traceId 统一贯穿。
- Nacos 环境隔离和权限控制。
- 密钥管理、轮换和服务间认证。
- 多实例、故障恢复和容量测试。

## 8. 里程碑与验收顺序

| 里程碑 | 内容 | 进入条件 | 完成标准 |
| --- | --- | --- | --- |
| M2.1 | Attempt 并发与 Outbox 抢占 | 当前代码基线 | 并发状态一致、事件单实例持有 |
| M2.2 | 死信、消费记录、人工补偿 | M2.1 完成 | 失败可查询、可重放、可审计 |
| M2.3 | 第二阶段端到端验收 | M2.2 完成 | 支付成功至入账自动化验收通过 |
| M3.1 | 退款和资金冲正 | M2.3 通过 | 全额/部分退款和冲正闭环 |
| M3.2 | 渠道账单与日对账 | M3.1 稳定 | 平账和差异单自动生成 |
| M3.3 | 运营处置与可观测性 | M3.2 完成 | 人工处置、审批、审计、告警闭环 |

推荐验收顺序：

1. 管理员认证、角色和数据权限。
2. 配置草稿、审核、发布、快照和回滚。
3. 订单和 Attempt 状态机及并发行为。
4. 回调签名、去重和防重放。
5. Outbox 原子抢占、claim token、失败重试和 DEAD。
6. Trade/Fund 管理接口内部凭证和角色边界。
7. RocketMQ 消费、DLQ 和人工重放。
8. Fund 幂等入账和差异校验。
9. 服务与 Broker 故障恢复。
10. 退款、资金冲正和退款差异。
10. 渠道账单、日对账和人工处置。

## 9. 近期执行顺序

下一步严格按以下顺序推进：

1. 补充 Attempt 查询调度、Outbox claim token 和管理接口权限测试。
2. 用真实 MySQL 验证 V4～V7、Fund V3～V4 迁移及多实例锁竞争。
3. 用真实 RocketMQ 验证重试、DLQ、人工重放和 Broker/服务重启恢复。
4. 增加 Gateway、Trade、Fund 管理接口安全测试并确认生产端口隔离。
5. 补充模拟渠道状态持久化、事件 DTO 序列化兼容和链路 ID 透传。
6. 验收通过后正式启动第三阶段退款领域开发。

## 10. 相关文件

- `README.md`：构建、运行和接口示例
- `docker-compose.yml`：RocketMQ Broker、MinIO 和本地网络
- `infra/rocketmq/broker.conf`：RocketMQ Broker 本地配置
- `infra/mysql/init/00-databases.sql`：数据库创建
- `infra/mysql/init/10-mvp-schema.sql`：MVP 初始化表
- `trade-service/src/main/resources/db/migration/`：Trade Flyway 迁移
- `trade-service/src/main/java/com/example/payments/trade/service/application/PaymentAttemptService.java`：Attempt 生命周期和订单协调
- `trade-service/src/main/java/com/example/payments/trade/service/application/PaymentOutboxPublisher.java`：Outbox 发布
- `fund-service/src/main/java/com/example/payments/fund/service/application/PaymentSuccessEventConsumer.java`：支付成功事件消费
- `fund-service/src/main/java/com/example/payments/fund/service/interfaces/rest/LedgerController.java`：内部幂等入账接口
