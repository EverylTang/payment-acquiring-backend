# 项目现状、Nacos 配置与内部管理后台开发计划

> 更新时间：2026-08-21
>
> 适用仓库：`payment-acquiring-backend`
>
> 本文记录当前代码、Nacos 配置约定、已实现能力、内部管理后台规划和下一步优先级。Nacos 中的实际配置以配置中心为准，本文不保存真实密码、渠道密钥或商户 API Key。

## 1. 当前系统概览

后端采用 Maven 多模块和 Spring Boot 微服务结构：

| 服务 | 默认端口 | 当前职责 | 当前状态 |
| --- | ---: | --- | --- |
| `gateway-service` | 8080 | 统一入口、请求头清洗、内部接口保护、请求 ID 透传 | 已实现最小网关过滤能力 |
| `platform-service` | 8081 | 产品、路由、费率、风控配置快照和渠道健康查询 | 已实现模拟配置快照接口 |
| `trade-service` | 8082 | 订单创建、查询、状态流转、取消、回调和订单持久化 | 已实现 MVP 订单闭环 |
| `fund-service` | 8083 | 支付成功后的资金分录写入 | 已实现幂等入账接口 |

基础设施包括 MySQL 8.4、Redis 7.2、Nacos 2.x、RocketMQ 5.2 和 MinIO。仓库的 Compose 主要负责 RocketMQ Broker 和 MinIO，默认复用本机已有的 MySQL、Redis、Nacos、RocketMQ NameServer 及 `local-dev-network`。

## 2. Nacos 配置约定

### 2.1 统一 namespace、group 和 DataId

四个服务统一使用：

| 配置项 | 当前值 | 覆盖方式 |
| --- | --- | --- |
| Nacos 地址 | `127.0.0.1:8848` | `NACOS_ADDR` |
| namespace | `payment` | `NACOS_NAMESPACE` |
| group | `PAYMENT_GROUP` | `NACOS_GROUP` |
| 配置格式 | `yml` | 固定为 `spring.cloud.nacos.config.file-extension=yml` |
| 默认环境 | `dev` | 使用 `--spring.profiles.active` 覆盖 |
| 默认账号 | `nacos` | `NACOS_USERNAME`、`NACOS_PASSWORD` |

每个服务从 Nacos 导入公共配置和服务环境配置：

| 类型 | DataId |
| --- | --- |
| 公共配置 | `application.yml` |
| Gateway | `gateway-service-dev.yml`、`gateway-service-test.yml`、`gateway-service-prod.yml` |
| Platform | `platform-service-dev.yml`、`platform-service-test.yml`、`platform-service-prod.yml` |
| Trade | `trade-service-dev.yml`、`trade-service-test.yml`、`trade-service-prod.yml` |
| Fund | `fund-service-dev.yml`、`fund-service-test.yml`、`fund-service-prod.yml` |

上述 DataId 位于 namespace `payment`、group `PAYMENT_GROUP`。配置导入使用 `optional:nacos`，Nacos 不可用时不会在导入阶段直接阻断启动，但数据库、Redis 等外部依赖缺少配置时仍可能启动失败。

### 2.2 配置职责

`application.yml` 负责公共约定：Spring 基础配置、Actuator、日志和请求追踪、MySQL/Redis/RocketMQ/MinIO 参数名称、连接池、超时和序列化配置。各服务环境 DataId 负责端口、数据库名、环境差异、Gateway 内部令牌、日志级别、连接池和资源限制。

- Gateway：8080
- Platform：8081，当前不访问数据库，代码显式排除 `DataSourceAutoConfiguration`
- Trade：8082，使用 `pay_trade`
- Fund：8083，使用 `pay_fund`
- 服务注册元数据包含 `environment` 和 `version`
- MySQL、Redis 密码只在 Nacos 或密钥系统维护，不提交到 Git

### 2.3 启动和发布检查

```bash
java -jar trade-service/target/trade-service-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

发布配置时按以下顺序执行：确认 `payment` namespace 和 `PAYMENT_GROUP`；发布公共 DataId；发布各服务环境 DataId；检查 YAML、端口、数据库名和敏感字段；重启或刷新配置；检查服务注册和健康状态。

```bash
curl -u nacos:'<password>' \
  'http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=application.yml&group=PAYMENT_GROUP&tenant=payment'

curl http://127.0.0.1:8080/actuator/health
curl http://127.0.0.1:8082/actuator/health
curl http://127.0.0.1:8083/actuator/health
```

## 3. 当前已实现逻辑

### 3.1 网关

- 基于 Spring Cloud Gateway 提供统一入口。
- `/api/internal/**` 请求执行 `gateway.security.internal-token` 校验。
- 清理 Hop-by-hop 请求头和客户端伪造的 `X-User-Id`、`X-Merchant-Id`、`X-Roles`。
- 生成或透传合法 `X-Request-Id`，并写入响应头。
- 完整路由、认证、限流、后台登录和审计能力尚未完成。

### 3.2 平台配置

当前 `platform-service` 提供模拟配置快照：

```text
GET /api/internal/v1/configurations/snapshot
  ?merchantId=...&productCode=...&paymentMethod=...&currency=...
GET /api/internal/v1/configurations/channels/{channelId}/health
```

快照包括产品能力、模拟渠道、优先级、权重、费率、计费模式、风控决策、候选渠道和配置版本。当前数据为代码内模拟值，尚未落库，也未接入真实商户、产品、渠道和费率管理。

### 3.3 交易订单

| 接口 | 能力 |
| --- | --- |
| `GET /api/v1/payments/orders/health` | 健康检查 |
| `POST /api/v1/payments/orders` | 创建订单，要求 `Idempotency-Key` |
| `GET /api/v1/payments/orders/{orderId}` | 查询订单详情 |
| `GET /api/v1/payments/orders/{orderId}/status` | 查询订单状态 |
| `POST /api/v1/payments/orders/{orderId}/cancel` | 取消未终态订单 |
| `POST /api/v1/payments/orders/{orderId}/callback?status=SUCCESS` | 模拟支付回调 |

已实现商户幂等键和商户订单号唯一约束、MySQL 真实落库、路由和计费快照、默认 30 分钟过期、查询/取消/回调、终态保护和并发创建竞态处理。真实渠道执行、超时任务和回调签名校验尚未接入。

### 3.4 资金入账

`POST /api/internal/v1/ledger/payment-success` 将支付成功写入 `pay_fund.ledger_entry`，分录类型为 `PAYMENT_SUCCESS`、方向为 `CREDIT`。通过 `idempotency_key` 和数据库唯一键保证重复请求不重复入账，重复请求返回 `duplicate=true`。当前仍是内部直写接口，尚未由订单成功事件自动驱动。

### 3.5 数据库和基础设施

初始化脚本包含 `pay_trade.payment_order`、`pay_trade.payment_attempt`、`pay_fund.ledger_entry`，以及 `pay_platform`、`pay_audit` 等数据库的创建。RocketMQ Broker、MinIO、MySQL 初始化脚本和 Redis/Nacos 依赖已纳入本地方案。

## 4. 内部管理后台规划

### 4.1 定位和边界

内部管理后台面向平台运营、风控、财务、技术运维和客服人员，不对商户开放。后台负责配置、审核、观察和处置，不绕过 Trade 或 Fund 服务直接修改订单和账务数据。

当前后端尚未实现完整后台，现有 Platform 仅有模拟配置快照和渠道健康接口。本节是规划内容，不代表功能已完成。

### 4.2 菜单和功能范围

| 一级菜单 | 主要页面和功能 | 角色 | 优先级 |
| --- | --- | --- | --- |
| 工作台 | 交易概览、渠道成功率、待处理告警、待发布配置 | 运营、技术、财务 | P0 |
| 商户管理 | 商户列表/详情、启停、API 凭证、产品绑定、限额 | 运营、客服 | P0 |
| 产品与交易方式 | 逻辑产品、国家/地区、币种、支付方式、产品能力 | 产品、运营 | P0 |
| 渠道管理 | 渠道主数据、能力、凭证、健康检查、启停和灰度 | 运营、技术 | P0 |
| 路由与费率 | 路由规则、费率方案、商户/渠道专属规则、版本发布 | 运营、财务 | P0 |
| 风控管理 | 风控规则、优先级、黑白名单、命中记录、人工处置 | 风控 | P0 |
| 交易管理 | 订单、支付尝试、回调记录、人工重试申请 | 客服、运营 | P1 |
| 资金与对账 | 分录、退款、渠道账单、差异单和对账任务 | 财务 | P1 |
| 审计与系统 | 操作审计、审批流、Nacos 配置、服务健康、字典 | 管理员、技术 | P1 |

列表页负责筛选、批量操作和状态概览，详情页负责实体关系、版本和变更历史。配置页面必须区分草稿、审核中、已发布、已停用，不允许编辑已生效记录后改变历史语义。

### 4.3 核心实体和数据边界

产品描述“是什么交易”，计费规则描述“如何收费”，商户绑定描述“谁能使用”，渠道映射描述“通过谁执行”，风控策略描述“是否允许执行”。建议关系如下：

```text
Merchant
  └─ MerchantProduct
       └─ LogicalProduct
            ├─ ProductCapability
            ├─ PricingRuleSet
            ├─ RoutingRuleSet
            └─ RiskPolicy

Channel
  └─ ChannelCapability
       └─ ChannelProductMapping

LogicalProduct + Merchant/Channel/Region/Amount
  └─ RuleVersion
       ├─ RoutingRule
       ├─ PricingRule
       └─ RiskPolicy
```

| 对象 | 关键字段 | 责任 |
| --- | --- | --- |
| `merchant` | 商户号、名称、主体、状态、结算币种 | 商户主数据 |
| `merchant_credential` | Key ID、密钥摘要、状态、有效期 | 凭证轮换和撤销，不回显明文 |
| `logical_product` | 编码、名称、收款方向、状态 | 交易产品主数据 |
| `product_capability` | 国家、币种、支付方式、金额范围 | 产品支持的交易能力 |
| `merchant_product` | 商户、产品、费率方案、路由方案、状态 | 商户可用范围 |
| `channel` | 渠道编码、提供商、环境、状态 | 渠道主数据和生命周期 |
| `channel_capability` | 国家、币种、支付方式、限额 | 渠道实际能力 |
| `routing_rule` | 作用域、条件、渠道、权重、优先级、版本 | 渠道候选和命中顺序 |
| `pricing_rule` | 作用域、费率类型、费率、固定费、上下限、版本 | 计费规则 |
| `risk_policy` | 条件、动作、优先级、版本、生效时间 | 放行、拒绝、审核和限额 |
| `config_release` | 配置域、版本、状态、发布人、发布时间 | 草稿、审批、发布和回滚 |

订单只保存发布时的 `route_snapshot_json`、`pricing_snapshot_json` 和风控结果；规则修改不改变历史订单。支付尝试、分录和审计记录由各领域服务负责，后台通过查询接口聚合展示。

### 4.4 规则优先级和计费

规则命中顺序建议为：

1. 商户 + 产品 + 支付方式 + 国家/币种 + 金额区间。
2. 商户 + 产品 + 支付方式 + 国家/币种。
3. 渠道 + 产品 + 支付方式 + 国家/币种。
4. 产品 + 支付方式 + 国家/币种默认规则。
5. 平台默认规则；仍未命中则拒绝交易，不静默使用未知费率。

同一作用域、金额区间和生效时间内不得存在相同优先级的有效规则。发布前做规则重叠检测，详情页展示命中规则、回退层级和原因。

示例：商户 A 使用 `CARD-US-USD`，订单 `100.00 USD`：

| 匹配层级 | 费率 | 固定费 | 命中结果 |
| --- | ---: | ---: | --- |
| 商户专属 | `2.00%` | `0.30 USD` | 优先命中，费用 `2.30 USD` |
| 渠道默认 | `2.40%` | `0.20 USD` | 商户规则不存在时回退 |
| 平台默认 | `2.80%` | `0.00 USD` | 最后兜底 |

费率页面必须显示百分比、固定费币种、计费模式（外加/内含）、金额上下限、有效时间和规则版本，禁止只显示无单位的 `0.02`。

### 4.5 后台关键流程

- 商户开通：创建商户 → 配置凭证 → 绑定逻辑产品 → 绑定国家/币种/支付方式 → 绑定费率和路由 → 配置风控 → 审核 → 发布 → 验证快照。
- 渠道接入：创建渠道 → 保存加密凭证 → 配置能力 → 配置回调和签名 → 健康检查 → 设置权重 → 灰度发布。
- 规则变更：新建草稿 → 冲突校验 → 提交审核 → 发布版本 → 观察命中率和成功率 → 必要时回滚。
- 风险处置：查看命中记录 → 查看订单/商户/渠道上下文 → 放行、拒绝或人工审核 → 留存理由 → 写入审计。

### 4.6 权限、安全和审批

- 采用 RBAC，至少区分管理员、运营、风控、财务、客服、技术运维和只读审计角色。
- 商户凭证、渠道密钥和 Nacos 敏感配置只展示摘要或执行轮换，不回显明文。
- 配置发布、渠道启停、风控变更、人工补单和资金处置支持二人审批或可配置审批策略。
- 所有写操作记录操作者、请求 ID、变更前后摘要、审批人、原因和发布时间。
- 订单和账务默认只读；退款、重试、补单等高风险操作必须有权限、幂等键、二次确认和审计。

## 5. 已知边界和风险

- 平台配置是代码内模拟数据，尚不能支撑真实运营配置。
- 管理后台尚未形成独立认证、权限、菜单、审计和审批能力。
- 订单尚未真正调用渠道执行支付；回调仍是模拟入口，缺少签名校验、重放防护和原文留存。
- 订单成功与资金入账没有可靠事件链路，存在跨服务最终一致性缺口。
- RocketMQ 已准备本地 Broker，但 Outbox、发布、消费幂等、失败重试和死信尚未实现。
- Platform 排除了数据源自动配置，接入配置落库时需要重新设计持久层和迁移策略。
- 可观测性主要依赖健康检查和日志，缺少指标、链路追踪、告警和审计查询。
- Nacos 为 `optional` 导入；生产环境需要增加配置完整性检查，避免配置缺失时使用不安全缺省值启动。

## 6. 更新后的开发优先级

### P0：建立可运营的后台和配置闭环

1. **后台基础壳与权限**
   - 完成登录、RBAC、菜单权限、数据权限、操作审计和统一错误处理。
   - 完成工作台、商户、产品、渠道、路由、费率、风控的基础列表、详情和状态流转。
   - 所有配置写操作先落草稿，不直接影响线上交易。

2. **配置域落库与版本发布**
   - 建立逻辑产品、产品能力、商户产品、渠道能力、路由、费率和风控模型。
   - 实现草稿、审核、发布、回滚、版本和生效时间。
   - 将 Platform 模拟快照改为按已发布版本查询，并保留订单快照。
   - 发布前校验规则重叠、渠道能力、费率单位、敏感字段和配置完整性。

3. **商户与渠道最小运营闭环**
   - 支持商户创建、启停、凭证轮换、产品绑定和基础限额。
   - 支持渠道创建、能力配置、密钥托管、健康检查、启停和灰度权重。
   - 接入一个模拟或沙箱渠道，让后台配置驱动可验证支付链路。

4. **真实支付链路基础**
   - 定义统一渠道适配器，接入下单、查询、取消和回调签名校验。
   - 完善订单状态迁移、支付超时、渠道查询补偿和 `payment_attempt`。
   - Trade 增加 Outbox 发布支付成功事件，Fund 消费并幂等入账。

### P1：完善风控、交易处置和财务运营

1. 风控规则、黑白名单、限额、频控、命中记录、人工审核和规则灰度。
2. 后台订单、支付尝试、回调原文、状态时间线和人工重试申请。
3. 退款、部分退款、退款尝试、资金冲正、渠道账单、日对账和差异单。
4. 外部签名、防重放、服务身份、短期令牌、密钥轮换和高风险操作审批。
5. 工作台增加商户、国家、支付方式、渠道和金额区间的运营指标。

### P2：生产化和规模化

1. 增加成功率、延迟、超时、重试、路由命中、风控命中、队列堆积和入账差异指标，接入链路追踪和告警。
2. 补齐状态机、并发幂等、重复回调、规则优先级、权限、发布回滚和资金重复入账测试。
3. 使用 Testcontainers 覆盖 MySQL、Redis、Nacos、RocketMQ，补齐 CI 和启动冒烟测试。
4. 将 Nacos 按 dev/test/prod 分离权限，接入密钥管理、变更审计、审批、备份和回滚。

## 7. 建议验收顺序

1. 管理员登录后按角色看到正确菜单、按钮和数据范围。
2. Nacos 配置读取成功，服务注册到 `payment` namespace。
3. 创建商户、绑定逻辑产品、配置渠道能力和费率规则。
4. 规则草稿通过冲突校验，审批后发布并可回滚。
5. 配置快照命中正确的商户、渠道、金额区间和优先级规则。
6. 所有服务健康检查通过，订单重复请求返回同一订单。
7. 模拟或真实渠道支付，验证回调签名、状态迁移和 `payment_attempt`。
8. 支付成功事件只触发一次有效入账，重复消息可安全重试。
9. 风控命中、人工处置、退款、对账差异和高风险操作均有审计记录。
10. 服务重启、Nacos 刷新、网络失败和发布回滚后链路仍可恢复。

## 8. 相关文件

- `README.md`：仓库构建、运行和 MVP 接口示例
- `*/src/main/resources/application.yml`：各服务 Nacos 导入和服务发现基线
- `infra/mysql/init/00-databases.sql`：数据库创建
- `infra/mysql/init/10-mvp-schema.sql`：MVP 表结构
- `docker-compose.yml`：RocketMQ Broker、MinIO 和本地网络
- `trade-service/src/main/java/.../OrderService.java`：订单状态和幂等逻辑
- `platform-service/src/main/java/.../ConfigurationController.java`：配置快照接口
- `fund-service/src/main/java/.../LedgerController.java`：幂等入账接口
- `gateway-service/src/main/java/.../GatewayRequestFilter.java`：网关安全过滤逻辑
