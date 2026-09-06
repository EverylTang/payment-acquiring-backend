# 支付收单系统设计文档

> 更新时间：2026-09-03
>
> 适用仓库：`payment-acquiring-backend`、`payment-acquiring-frontend`

## 1. 目标与边界

系统提供面向运营、风控和财务人员的支付收单管理能力，并以可靠支付事件驱动资金入账。管理端负责身份权限、商户与产品主数据、渠道与规则配置、发布审计和运营处置；交易与资金服务负责订单执行、消息投递、账务分录和退款冲正。

核心原则：

- 运行配置先审核、发布后生效；非运行主数据可直接保存，但所有变更必须审计。
- 后端是权限、状态和业务规则的唯一裁决方；前端权限隐藏仅改善交互。
- 已被订单、配置或账务引用的数据不物理删除，改为停用并保留历史。
- 凭证、密码和渠道密钥仅可写入、轮换或脱敏展示，禁止通过常规接口、日志或审计返回明文。
- 数据库以 `docs/database/payment-acquiring-complete.sql` 作为唯一新环境初始化入口；生产变更通过受控发布流程执行。

## 2. 架构与基础设施

### 2.1 服务职责

| 服务 | 默认端口 | 职责 |
| --- | ---: | --- |
| `gateway-service` | 8080 | 统一入口、路由、内部接口保护、请求头清洗和请求 ID 透传 |
| `platform-service` | 8081 | 管理员认证、RBAC、商户/产品/渠道配置、规则发布与快照 |
| `trade-service` | 8082 | 订单、Payment Attempt、渠道适配、回调、Outbox 与消息发布 |
| `fund-service` | 8083 | 支付成功入账、退款冲正、消费幂等、对账与资金台账 |

主支付链路：

```text
管理员认证与配置发布
  -> Gateway 鉴权与路由
  -> Trade 创建订单与 Payment Attempt
  -> 渠道执行、查询、取消和签名回调
  -> Attempt 协调订单状态
  -> PAYMENT_SUCCEEDED 同事务写入 Outbox
  -> RocketMQ 至少一次投递
  -> Fund 幂等写入 ledger_entry
```

退款链路复用相同可靠事件框架：退款成功后写入 `REFUND_SUCCEEDED`，Fund 创建关联原支付分录的 `REFUND` 或 `REVERSAL` 分录，并校验累计冲正金额。

### 2.2 基础设施与配置

本地依赖为 MySQL 8.4、Redis 7.2、Nacos 2.3.2、RocketMQ 5.2.0 和 MinIO。基础设施由 Docker 或平台独立维护，应用通过 Nacos 和服务端口连接；仓库不保存真实密码、渠道密钥或商户 API Key。

渠道运行配置统一保存在 `channel.config_json`：`settings` 是不预设字段的自定义接入参数 JSON；`credentials` 保存商户号、应用标识、加签验签密钥及其他敏感渠道值，并由后台页面直接管理和回显。签名方案保存为 `signature_profile`，签名密钥角色可在 `credentials.signatureSecretRole` 配置，默认为 `requestSigningKey`。凭据不会写入操作审计或应用日志；支付尝试快照按现有运行协议保存创建支付所需的凭据快照。内部快照调用要求平台与交易服务使用相同的 `GATEWAY_INTERNAL_TOKEN`。

签名方案由渠道管理下拉框受控选择，当前支持 `NONE`、`MD5_KEY_SUFFIX_V1`、`SHA256_KEY_SUFFIX_V1`、`HMAC_SHA256_V1`、`HMAC_SHA512_V1`、`RSA_SHA256_V1` 与模拟渠道兼容方案。交易服务在调用适配器前自动按字典序构造 `key=value` 待签名串，并从 `requestSigningKey`（或配置的 `signatureSecretRole`）读取 KMS 凭据。`signatureFields` 可指定逗号分隔的待签名字段，`signatureFieldName` 可指定渠道请求中的签名字段名；渠道适配器负责将生成的签名放入渠道要求的位置。渠道专属的字段编码、时间戳、嵌套参数及回调验签必须以服务商文档为准。

Nacos 默认约定：

| 配置 | 默认值 | 覆盖方式 |
| --- | --- | --- |
| 地址 | `127.0.0.1:8848` | `NACOS_ADDR` |
| namespace | `payment` | `NACOS_NAMESPACE` |
| group | `PAYMENT_GROUP` | `NACOS_GROUP` |
| 环境 | `dev` | `spring.profiles.active` |

每个服务加载 `application.yml` 与 `{service-name}-{profile}.yml`。生产环境必须校验关键配置完整性，并隔离 Nacos、服务间凭证和管理端口。

## 3. 管理平台设计

### 3.1 功能域

```text
系统管理：用户、角色、菜单、权限点、数据范围、操作审计
商户中心：商户资料、联系人、凭证、产品绑定和关联查询
产品中心：逻辑产品、产品能力、国家/币种范围、商户产品
运营配置：渠道、路由、费率、风控、草稿、审批、发布、回滚
运营处置：订单、Attempt、回调、Outbox、消费记录、退款、对账差异
```

产品范围由以下模型分层，避免产品定义与商户专属配置耦合：

```text
logical_product       产品定义与默认市场
product_capability    产品通用能力上限；包含对客支付方式与渠道支付方式映射
merchant_product      商户是否开通该产品
channel_capability    渠道可处理的国家、币种和支付方式
country_currency_master 国家/币种启用组合
```

产品默认市场必须使用已启用的国家、币种及其关联组合。产品能力中的对客支付方式是商户/API 选择的支付方式，渠道支付方式是提交渠道的具体方式；支付方式配置不区分国家或币种。商户产品配置不得突破产品能力上限；商户、产品或能力停用后禁止新配置和新交易入口，但历史记录可查询。

### 3.2 权限与数据范围

核心对象及关系：

```text
admin_user <-> admin_role <-> admin_menu
                         <-> admin_permission
                         <-> admin_role_data_scope
```

内置角色：

| 角色 | 定位 |
| --- | --- |
| `ADMIN` | 全量系统、菜单、权限和数据管理 |
| `OPS` | 商户、产品、渠道、路由与运营处置 |
| `RISK` | 风控策略、风险信息和授权查询 |
| `FINANCE` | 费率、结算、账务和授权查询 |
| `READONLY` | 授权范围内只读访问 |

权限编码使用 `资源:动作`，例如 `merchant:update`、`product-capability:status`、`config-release:publish`。列表、详情、创建、修改、启停、分配、审批和发布必须是独立权限点。

数据范围在后端查询层执行：`ALL`、`ASSIGNED`、`SELF`。订单、商户产品、路由、费率和风控查询沿商户维度复用该过滤规则。前端根据当前用户的菜单和权限控制导航、按钮与路由守卫；后端以 `@PreAuthorize` 和应用服务校验最终拦截未授权调用。

安全约束：

- BCrypt 保存密码，禁止返回哈希。
- 禁止禁用最后一个有效系统管理员，禁止越权给自身分配角色。
- 被引用的角色、菜单和权限使用停用或逻辑删除。
- 权限变化后失效缓存；登录失败、锁定和最后登录等安全事件落库。
- 用户、角色、权限、凭证、产品和运行配置操作都写入统一操作审计。

### 3.3 商户与主数据

`merchant` 是运行时稳定标识，扩展信息分拆保存：

- `merchant_profile`：法定名称、主体类型、注册地、行业、税务、风险等级、官网、商品描述、账单描述符、支持信息和地址。
- `merchant_contact`：联系人和通知方式。
- `merchant_credential`：凭证版本、状态和轮换时间；仅保存加密值或摘要。
- 商户数据范围由用户/角色关联维护。

国家与币种管理以国家列表为入口。每个国家可查看关联币种、启停组合，或新建币种并自动建立关联；三位国家代码不再使用。新币种创建与国家关联在同一事务中完成。

### 3.4 发布与审计

影响交易运行的产品能力、商户产品、路由、费率和风控策略采用：

```text
草稿 -> 提交审核 -> 审核通过 -> 发布 -> 生效
                    -> 驳回或撤回
```

每次变更记录操作者、角色、请求 ID、对象类型和 ID、操作、前后摘要、结果、失败原因与时间。敏感字段只记录脱敏摘要。后台不直接修改订单、Attempt 或账务表，所有处置必须调用领域服务。

## 4. 交易与资金设计

### 4.1 订单、Attempt 与渠道

订单状态：

```text
CREATED -> PAYING -> SUCCESS / FAILED / UNKNOWN / CANCELED
UNKNOWN -> PAYING / SUCCESS / FAILED / CANCELED
```

Attempt 状态：

```text
CREATED -> PROCESSING -> SUCCESS / FAILED / TIMEOUT / CANCELED / UNKNOWN
UNKNOWN -> PROCESSING / SUCCESS / FAILED / TIMEOUT / CANCELED
```

终态不得被后续结果覆盖。状态更新使用版本号 CAS；CAS 失败时重新读取真实状态，避免竞争线程以过期结果协调订单。真实渠道接入时应把“创建 Attempt、外部调用、条件更新”拆分，禁止在数据库事务中持有长时间网络调用。

渠道适配器统一提供创建、查询、取消、退款与回调验签能力。真实渠道必须使用供应商协议规定的签名、时间戳、nonce 和防重放策略；模拟渠道用于开发验证，不替代供应商协议验收。

### 4.2 Outbox、消息与资金幂等

Attempt 成功后，在同一事务完成 Attempt 条件更新、订单成功协调和 `PAYMENT_SUCCEEDED` Outbox 写入。Outbox 以事件唯一键、条件 claim、过期锁恢复、claim token、有限重试、指数退避和 `DEAD` 状态保障多实例投递。

RocketMQ 采用至少一次投递。Fund 保存消费记录、payload 摘要、处理状态、失败原因与 processing lease；重复事件校验 payload，重复幂等键校验订单、商户、金额、币种和分录字段，冲突不能被视为成功。

```text
PAYMENT_SUCCEEDED
  -> LedgerEntryApplicationService
  -> entry_type=PAYMENT_SUCCESS, debit_credit=CREDIT
  -> idempotency_key=payment-success:{orderId}
```

事件包含 `schemaVersion`、`occurredAt`、`producer`、`requestId`、`traceId` 和 `aggregateVersion`。消费者拒绝未知版本；后续应抽取稳定公共 DTO 并补足序列化兼容测试。

### 4.3 退款、对账与运营处置

退款支持全额、部分和多次部分退款，使用幂等键与累计退款金额控制并发上限。退款 Attempt、回调去重、查询重试、可靠事件和 Fund 冲正沿用支付成功链路的可靠性模型。

渠道账单支持上传或定时下载，原始文件保存在 MinIO 并记录哈希、日期、渠道、版本与导入状态。日对账按订单号、渠道订单号、商户、金额、币种和状态匹配，差异类型为：

```text
MATCHED / CHANNEL_ONLY / PLATFORM_ONLY / AMOUNT_MISMATCH
CURRENCY_MISMATCH / STATUS_MISMATCH / DUPLICATE
```

差异认领、补单、冲正、忽略和关闭均要求权限、原因、二次确认和审计；高风险资金操作进入审批。

## 5. 代码与数据访问约定

### 5.1 后端分层

```text
<service>/service/controller  HTTP 边界、请求响应 DTO
<service>/service/service     应用服务、任务、消息消费者
<service>/service/model       MyBatis-Plus 实体与持久化模型
<service>/service/mapper      Mapper、Repository、Mapper XML
<service>/service/domain      领域对象和状态机
<service>/service/config      配置属性
<service>/service/security    认证与授权
```

Controller 只处理协议和鉴权编排；业务规则在应用服务和领域层；数据库访问经由 `service` 调用 `mapper`。Gateway 保持 `filter` 层。

### 5.2 前端组织

业务页面按 `src/modules/` 组织，例如 `auth`、`merchant`、`product`、`master-data`、`permission`、`user`、`refund`、`operations`。`App.vue` 只维护应用壳与模块切换；共享请求和认证逻辑放在 `src/api.ts`、`src/auth.ts`；每个模块维护自己的 `api.ts`，模块间不直接相互引用页面组件。

列表提供分页、筛选、加载、空状态与错误状态；表单提供必填、格式、范围和防重复提交；危险操作展示影响范围并二次确认。

### 5.3 持久化规则

所有服务统一使用 MyBatis-Plus 访问 MySQL：

- 新表创建 `@TableName` 实体和 `BaseMapper`。
- 单表读写优先 `LambdaQueryWrapper`、`LambdaUpdateWrapper` 和 Mapper 方法。
- 多表、聚合和数据库特性 SQL 放入 Mapper XML 或 Mapper 注解。
- 分页使用 MyBatis-Plus `IPage`/`Page` 并应用数据范围过滤。
- 事务使用 Spring `@Transactional`，事务内仍通过 Mapper 或 `MybatisPlusClient` 访问数据库。
- 禁止在业务代码中使用 Spring JDBC、`JdbcClient`、`JdbcTemplate`、`DriverManager` 或直接 JDBC API。

## 6. 当前状态与验收

当前主体能力已覆盖平台配置、订单与 Attempt、模拟渠道、Outbox、RocketMQ 投递、Fund 幂等入账、退款/冲正基础、账单摘要导入和运营处置页面。自动化测试已覆盖状态机、模拟渠道、Outbox 成功与失败分支、Fund 正常和重复消费、payload 冲突及未知事件版本等核心分支。

仍需完成的 P0 验收：

1. 真实 MySQL 下 Attempt 并发 CAS、Outbox 多实例 claim、锁过期与 claim token 边界。
2. 真实 RocketMQ 下最大重试、DLQ、人工重放、Broker 停止恢复及 Trade/Fund 重启恢复。
3. Gateway、Trade 与 Fund 管理接口的内部凭证、角色边界和生产端口隔离测试。
4. 新环境空库初始化、数据库发布 Job、备份回滚和版本校验流程。

P1 收尾包括模拟渠道状态持久化、Attempt 调度集成测试、稳定事件 DTO、请求/追踪 ID 全链路透传、真实渠道签名适配与账单文件版本管理。

生产化指标至少覆盖 Outbox 待发布数与最老年龄、发布重试和 DEAD、消费堆积与 DLQ、支付成功未入账、Attempt 超时、退款成功未冲正、对账差异数量和处理时长。Prometheus、Grafana、OpenTelemetry、告警、密钥管理、多实例故障恢复和容量测试在上线前完成。

## 7. 里程碑

| 里程碑 | 目标 | 完成标准 |
| --- | --- | --- |
| M2.1 | Attempt 并发与 Outbox 抢占 | 状态一致、事件仅单实例持有 |
| M2.2 | 死信、消费记录与人工补偿 | 失败可查询、可重放、可审计 |
| M2.3 | 支付成功到入账端到端验收 | 自动化链路通过并可故障恢复 |
| M3.1 | 退款与资金冲正 | 全额/部分退款和冲正闭环 |
| M3.2 | 渠道账单与日对账 | 平账与差异单自动生成 |
| M3.3 | 运营处置与可观测性 | 审批、审计、告警和人工处置闭环 |

验收顺序：权限与数据范围、配置发布、订单与 Attempt 并发、回调签名与去重、Outbox 和管理接口边界、消息消费与资金幂等、故障恢复、退款与对账、可观测性与容量。

## 8. 相关入口

- `docs/database/payment-acquiring-complete.sql`：唯一新环境数据库初始化入口。
- `README.md`：构建、运行和接口示例。
- `trade-service/.../PaymentAttemptService.java`：Attempt 生命周期和订单协调。
- `trade-service/.../PaymentOutboxPublisher.java`：Outbox 发布。
- `fund-service/.../PaymentSuccessEventConsumer.java`：支付成功事件消费。
- `fund-service/.../LedgerEntryApplicationService.java`：统一幂等入账。
