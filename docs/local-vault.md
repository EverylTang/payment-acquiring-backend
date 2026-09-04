# 本地 Vault 与 Trade 服务

本文档仅适用于本机开发。这里的开发令牌和模拟渠道密钥是公开的固定默认值，不能用于测试、预发布或生产环境。

## 部署和初始化

前提是 Docker Desktop 已启动。以下脚本会启动名为 `payment-vault` 的 Vault 1.17.6 开发模式容器，监听回环地址 `127.0.0.1:8200`，并写入模拟渠道的两个默认凭据。

```bash
./scripts/setup-local-vault.sh
```

默认值：

| 项目 | 默认值 | 用途 |
| --- | --- | --- |
| Vault 地址 | `http://127.0.0.1:8200` | Trade 服务访问地址 |
| Vault 开发令牌 | `dev-root-token` | 仅用于本地开发环境的 Vault API 认证 |
| 服务间令牌 | `payment-local-internal-token` | Platform 与 Trade 内部配置快照接口认证 |
| KV v2 路径 | `secret/payments/channels/simulated` | 模拟渠道的密钥存储位置 |
| `requestSigningKey` | `payment-local-simulated-signing-secret` | 创建支付请求签名 |
| `callbackVerifyKey` | `payment-local-simulated-signing-secret` | 回调验签 |

Vault 开发模式不会保证重启后的数据持久化。容器或 Docker Desktop 重启后再次执行脚本即可恢复默认配置。停止或删除该容器只影响本地开发数据。

`keyVersion` 对 Vault KV v2 使用正整数或 `v` 加正整数，例如 `v1` 会读取 KV 的 `version=1`；留空时读取当前版本。

## 后端交互

Trade 服务的密钥提供方由以下环境变量选择：

```bash
export TRADE_SECRETS_PROVIDER=vault
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=dev-root-token
export GATEWAY_INTERNAL_TOKEN=payment-local-internal-token
```

设置后重启 Platform 和 Trade 服务。Platform 与 Trade 必须使用相同的 `GATEWAY_INTERNAL_TOKEN`，否则 Trade 无法取得内部渠道配置快照。`vault` 模式会启用 `VaultChannelSecretResolver`，读取 KV v2 API；默认 `env` 模式仍使用 `env://VARIABLE_NAME`，兼容现有本地启动方式。

本机可用 `./scripts/run-local-trade.sh` 启动 Trade；脚本继承以上环境变量，不依赖 IDE 的 Java 启动参数。

渠道绑定保存的是引用，而不是密钥：

```text
vault://secret/data/payments/channels/simulated#requestSigningKey
vault://secret/data/payments/channels/simulated#callbackVerifyKey
```

运行时的调用链为：Platform 的内部配置快照返回上述引用 -> Trade 的 `PlatformChannelConfigurationClient` 创建 `ChannelRuntimeContext` -> 渠道适配器调用 `runtime.secret(role)` -> `VaultChannelSecretResolver` 发起 `GET /v1/secret/data/payments/channels/simulated`，并只取 URI fragment 指定的字段。密钥不会写入数据库、配置快照、接口响应或日志。

完整新库初始化会从 `docs/database/payment-acquiring-complete.sql` 获得这两个默认 Vault 引用。已有本地数据库可执行：

```sql
UPDATE pay_platform.channel_secret_binding
SET secret_ref = CASE credential_role
  WHEN 'requestSigningKey' THEN 'vault://secret/data/payments/channels/simulated#requestSigningKey'
  WHEN 'callbackVerifyKey' THEN 'vault://secret/data/payments/channels/simulated#callbackVerifyKey'
END,
key_version = 'v1',
updated_at = CURRENT_TIMESTAMP(3)
WHERE channel_id = 'simulated-channel'
  AND credential_role IN ('requestSigningKey', 'callbackVerifyKey');
```

Trade 会将渠道 ID、适配器配置和每个凭证的引用与 `keyVersion` 保存到支付尝试快照中，但不会保存密钥值。渠道密钥轮换后，已创建支付的异步回调仍使用其原始版本验签；历史上没有该快照的支付尝试则保持与旧版本兼容，使用当前渠道配置。

## 验证

```bash
curl -fsS http://127.0.0.1:8200/v1/sys/health
curl -fsS -H 'X-Vault-Token: dev-root-token' \
  http://127.0.0.1:8200/v1/secret/data/payments/channels/simulated
```

第二个命令会返回密钥，避免在共享终端、CI 日志或截图中执行。常规验证只需要运行服务健康检查和支付 E2E：

```bash
TRADE_BASE_URL=http://127.0.0.1:8082 FUND_BASE_URL=http://127.0.0.1:8083 ./scripts/run-payment-e2e.sh
```

## 非本地环境

生产环境不能使用 dev 模式、固定令牌或固定密钥。Vault 应使用持久化存储、TLS、审计设备和 AppRole/Kubernetes/云工作负载身份；令牌通过部署平台的密钥注入提供。对于不可导出的私钥，应增加由 KMS 直接执行签名的接口，不能通过当前 `resolve` 方法取得私钥明文。

使用 Vault Agent 时，将轮换令牌写入 `VAULT_TOKEN_FILE`，Trade 会在每次 Vault 请求前重新读取该文件；生产 profile 使用 HTTP Vault 地址会在启动时被拒绝。
