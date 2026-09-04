# Vault 本地部署与操作手册

本文档用于本项目的本机开发环境。示例中的开发令牌和模拟渠道密钥均为固定公开值，严禁用于测试、预发布或生产环境。渠道配置中只保存 Vault 引用，绝不填写密钥明文。

## 1. 架构与约定

本地链路如下：

```text
Platform 渠道配置（secretRef + keyVersion）
  -> Trade 内部配置快照
  -> ChannelRuntimeContext
  -> Vault KV v2
```

Trade 读取密钥后仅在内存中用于请求签名或回调验签。密钥不会写入订单、支付尝试快照、配置快照、接口响应或日志。支付尝试快照只保存引用和版本，因此密钥轮换后，历史支付回调仍可使用创建支付时的版本验签。

本机默认资源：

| 项目 | 默认值 | 用途 |
| --- | --- | --- |
| Docker 容器 | `payment-vault` | Vault 开发模式容器 |
| Vault 地址 | `http://127.0.0.1:8200` | 仅监听本机回环地址 |
| Vault 开发令牌 | `dev-root-token` | 仅本地 Vault API 认证 |
| 服务间令牌 | `payment-local-internal-token` | Platform 与 Trade 内部配置认证 |
| KV v2 挂载 | `secret` | Vault 默认 KV v2 挂载 |
| 模拟渠道路径 | `secret/payments/channels/simulated` | 模拟渠道的密钥位置 |

## 2. 前置条件

需要 Docker Desktop、`curl` 和 Maven。确认 Docker 已可用：

```bash
docker info >/dev/null
```

以下命令均从仓库根目录执行：

```bash
cd /Users/pengzhen/Documents/Payment/payment-acquiring-backend
```

## 3. 部署与初始化

首次部署或 Vault 数据需要恢复时执行：

```bash
./scripts/setup-local-vault.sh
```

脚本会创建或启动 `payment-vault`，将 `8200` 映射到 `127.0.0.1:8200`，等待健康检查成功，并写入模拟渠道的 `requestSigningKey` 与 `callbackVerifyKey`。

可覆盖本地开发默认值，不要将真实密钥写入终端历史或提交到配置文件：

```bash
VAULT_CONTAINER=payment-vault \
VAULT_DEV_ROOT_TOKEN=dev-root-token \
VAULT_SIMULATED_CHANNEL_SECRET=payment-local-simulated-signing-secret \
./scripts/setup-local-vault.sh
```

Vault 开发模式无持久化保证。删除容器、Docker Desktop 重置或宿主机重启后，如数据不存在，再次运行初始化脚本即可恢复模拟数据。

## 4. 容器操作

```bash
# 状态与端口
docker ps --filter name=payment-vault

# 实时日志
docker logs -f payment-vault

# 停止后保留容器，后续可直接启动
docker stop payment-vault
docker start payment-vault

# 删除本地开发 Vault。该操作会丢失未持久化的开发数据。
docker rm -f payment-vault
```

删除容器后，必须重新执行 `./scripts/setup-local-vault.sh`。

## 5. Vault 健康与元数据检查

健康检查不会返回密钥，可安全用于日常联调：

```bash
curl -fsS http://127.0.0.1:8200/v1/sys/health
```

查看模拟渠道密钥的版本元数据，不输出密钥值：

```bash
docker exec \
  -e VAULT_ADDR=http://127.0.0.1:8200 \
  -e VAULT_TOKEN=dev-root-token \
  payment-vault \
  vault kv metadata get secret/payments/channels/simulated
```

以下 API 会返回明文密钥，仅能在本机私密终端用于故障排查，禁止在共享终端、CI 日志、录屏或截图中执行：

```bash
curl -fsS -H 'X-Vault-Token: dev-root-token' \
  http://127.0.0.1:8200/v1/secret/data/payments/channels/simulated
```

## 6. 写入渠道密钥并生成引用

支付渠道提供真实 API Key、签名密钥或证书后，将其按渠道写入 Vault。以下命令中的值是占位符，替换时不要将真实值提交到 Git：

```bash
docker exec \
  -e VAULT_ADDR=http://127.0.0.1:8200 \
  -e VAULT_TOKEN=dev-root-token \
  payment-vault \
  vault kv put secret/payments/channels/acme \
  requestSigningKey='<provider-request-signing-secret>' \
  callbackVerifyKey='<provider-callback-verification-secret>'
```

KV 路径与字段名决定渠道中填写的 `secretRef`：

```text
vault://secret/data/payments/channels/acme#requestSigningKey
vault://secret/data/payments/channels/acme#callbackVerifyKey
```

引用格式为 `vault://<mount>/data/<path>#<field>`：

- `<mount>` 是 KV v2 挂载名，例如 `secret`。
- `/data/` 是 KV v2 API 引用的一部分，不能省略。
- `<path>` 是写入 `vault kv put` 时挂载名之后的路径。
- `<field>` 是同一次写入中的字段名。

## 7. 渠道安全凭证填写

在 Platform 的渠道安全凭证中填写引用和版本，而不是密钥、Vault Token 或 Vault 地址。

模拟渠道使用以下两条记录：

| 凭证角色 | `secretRef` | `keyVersion` | 用途 |
| --- | --- | --- | --- |
| `requestSigningKey` | `vault://secret/data/payments/channels/simulated#requestSigningKey` | `v1` | 请求签名 |
| `callbackVerifyKey` | `vault://secret/data/payments/channels/simulated#callbackVerifyKey` | `v1` | 回调验签 |

当前后端接受 `v1` 或 `1` 形式的 `keyVersion`。留空表示读取 Vault 最新版本。对会异步回调的渠道，建议填写明确版本；已创建支付会继续使用其快照中的历史版本。

如果 Trade 使用环境变量模式而不是 Vault，引用格式为 `env://VARIABLE_NAME`，例如 `env://ACME_REQUEST_SIGNING_KEY`。此时 `TRADE_SECRETS_PROVIDER` 必须为 `env`，且环境变量名必须是大写字母、数字和下划线。

## 8. 密钥轮换

Vault KV v2 的每次 `kv put` 都会生成新版本。推荐流程：

1. 在渠道侧准备新密钥，但保留旧密钥验签能力。
2. 将新值写入同一 Vault 路径，记录命令输出或元数据中的新版本号。
3. 在 Platform 渠道安全凭证中将两个 `keyVersion` 更新为该版本，例如 `v2`。
4. 创建一笔新支付验证新版本签名和回调。
5. 等待旧支付的回调、退款和对账窗口结束后，再按 Vault 保留策略销毁旧版本。

本地演示轮换：

```bash
docker exec \
  -e VAULT_ADDR=http://127.0.0.1:8200 \
  -e VAULT_TOKEN=dev-root-token \
  payment-vault \
  vault kv put secret/payments/channels/simulated \
  requestSigningKey='<new-local-secret>' \
  callbackVerifyKey='<new-local-secret>'

docker exec \
  -e VAULT_ADDR=http://127.0.0.1:8200 \
  -e VAULT_TOKEN=dev-root-token \
  payment-vault \
  vault kv metadata get secret/payments/channels/simulated
```

将元数据中显示的版本更新到渠道绑定的 `keyVersion`。不要立即执行 `vault kv destroy` 或 `vault kv delete`，否则仍等待回调的历史支付可能无法验签。

## 9. 启动后端并验证

Platform 与 Trade 必须使用相同的内部令牌。先设置 Trade 环境变量：

```bash
export TRADE_SECRETS_PROVIDER=vault
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=dev-root-token
export GATEWAY_INTERNAL_TOKEN=payment-local-internal-token
export PLATFORM_SERVICE_URL=http://127.0.0.1:8081
```

启动 Trade：

```bash
./scripts/run-local-trade.sh
```

服务验证：

```bash
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:8082/actuator/health

TRADE_BASE_URL=http://127.0.0.1:8082 \
FUND_BASE_URL=http://127.0.0.1:8083 \
./scripts/run-payment-e2e.sh
```

运行时链路为：Platform 内部配置快照返回凭证引用与版本，Trade 创建 `ChannelRuntimeContext`，渠道适配器按角色解析密钥，`VaultChannelSecretResolver` 调用 Vault KV v2 API。密钥仅存在于本次运行的内存中。

## 10. Vault Agent 与超时配置

开发环境可使用 `VAULT_TOKEN`。使用 Vault Agent 时，Agent 将短期令牌写入文件，Trade 会在每次 Vault 请求前重新读取该文件：

```bash
export VAULT_TOKEN_FILE=/run/secrets/vault-token
unset VAULT_TOKEN
```

可按部署网络条件配置连接与读取超时，单位为毫秒：

```bash
export VAULT_CONNECT_TIMEOUT_MS=1000
export VAULT_READ_TIMEOUT_MS=2000
```

生产 profile 使用 HTTP Vault 地址会被 Trade 拒绝，必须配置 HTTPS 地址、可信 CA 和工作负载身份。

## 11. 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| `Vault 地址或访问令牌未配置` | 确认 `VAULT_ADDR` 与 `VAULT_TOKEN`，或配置可读的 `VAULT_TOKEN_FILE`。 |
| `无法从 Vault 读取渠道密钥` | 检查 Vault 容器健康、令牌策略、路径、字段名和 `keyVersion`。 |
| `Vault 密钥引用格式无效` | 确认格式为 `vault://secret/data/path#field`，包含 `/data/`，且不含查询参数。 |
| `渠道签名密钥未配置` | 确认渠道绑定角色与适配器期望的角色一致，模拟渠道需要 `requestSigningKey`、`callbackVerifyKey`。 |
| Trade 无法读取 Platform 配置 | 确认 Platform 与 Trade 的 `GATEWAY_INTERNAL_TOKEN` 一致，并检查 `PLATFORM_SERVICE_URL`。 |
| 轮换后历史回调验签失败 | 核查该支付尝试的快照是否含凭证引用和历史 `keyVersion`，并恢复 Vault 对应历史版本。 |

## 12. 生产部署要求

生产环境不使用 Vault dev mode、固定令牌、HTTP 地址或本文档的模拟密钥。至少应具备：

1. 持久化 Vault 存储、TLS、审计设备与备份恢复方案。
2. 最小权限策略，每个 Trade 工作负载只允许读取自己的渠道路径和字段。
3. AppRole、Kubernetes、云工作负载身份或 Vault Agent，禁止长期静态 Root Token。
4. 令牌通过部署平台的密钥注入提供，使用 `VAULT_TOKEN_FILE` 支持轮换。
5. 密钥轮换保留窗口覆盖支付、退款、回调与对账的最长处理时限。
6. 不可导出的私钥通过 KMS/HSM 签名接口使用，不能用当前 `resolve` 接口读取明文私钥。
