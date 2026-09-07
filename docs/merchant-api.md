# 商户收单 API

本文档描述商户可直接调用的订单、通知和退款接口。订单是唯一的收单业务对象，订单状态以查询接口和验签后的异步通知为准。

商户开通、后台配置和上线验收顺序见 [`merchant-onboarding.md`](merchant-onboarding.md)。

## 认证与通用约定

订单接口通过商户 API 凭证签名认证。商户请求需要携带以下请求头：

```http
X-Merchant-Key-Id: <API凭证ID>
X-Merchant-Timestamp: <Unix秒级时间戳>
X-Merchant-Nonce: <每次请求唯一的随机字符串>
X-Merchant-Signature: <签名结果>
```

创建订单还必须提供唯一的 `Idempotency-Key`。请求和响应使用 JSON，金额使用十进制定点数，币种使用 ISO 4217 代码。

`X-Merchant-Id` 和 `X-Gateway-Token` 是网关转发到 Trade 服务时使用的内部请求头，商户无需传递，也不能使用它们代替商户签名。

### 请求签名

网关使用商户请求的原始 HTTP 请求内容构造待签名原文，格式如下，每一项使用一个换行符 (`\n`) 分隔：

```text
HTTP_METHOD
RAW_PATH
NORMALIZED_QUERY
TIMESTAMP
NONCE
SHA256_RAW_BODY
```

字段规则：

- `HTTP_METHOD` 使用大写 HTTP 方法，例如 `POST`、`GET`。
- `RAW_PATH` 使用原始请求路径，例如 `/api/v1/payments/orders`。
- `NORMALIZED_QUERY` 为空时必须保留空行；有 query 时按 `&` 切分，按字符串字典序排序，再用 `&` 拼接。参数值不做二次解码或重新编码。
- `TIMESTAMP` 必须与 `X-Merchant-Timestamp` 完全一致，使用 Unix 秒级时间戳。
- `NONCE` 必须与 `X-Merchant-Nonce` 完全一致，且同一个 API 凭证不能重复使用。
- `SHA256_RAW_BODY` 是实际发送的原始请求体字节经过 SHA-256 后得到的小写十六进制字符串。签名后不得改变 JSON 的空格、字段顺序、数字格式或字符编码。

签名计算方式：

```text
X-Merchant-Signature = Base64URL-NoPadding(
    HMAC-SHA256(<商户API密钥>, <待签名原文>)
)
```

服务端还会校验 API 凭证处于 `ACTIVE` 状态且未过期、请求源 IP 是否在白名单内，以及时间戳与服务端时间的偏差不超过 300 秒。签名校验成功后才会将请求转发到交易服务；nonce 会写入平台数据库用于防重放。

当前 `Idempotency-Key` 用于订单幂等控制，不属于上述待签名原文；但它必须随创建订单请求一起发送，并在重试同一订单时保持不变。

Node.js 签名示例（`body` 必须使用签名时的同一字符串发送）：

```js
import crypto from "node:crypto";

const secret = "<商户API密钥>";
const body = JSON.stringify({
  merchantOrderNo: "M202409070001",
  appId: "1000",
  payModel: "CARD",
  country: "US",
  currency: "USD",
  amount: 100.00
});
const timestamp = Math.floor(Date.now() / 1000).toString();
const nonce = crypto.randomUUID();
const method = "POST";
const rawPath = "/api/v1/payments/orders";
const normalizedQuery = "";
const bodyHash = crypto.createHash("sha256").update(body, "utf8").digest("hex");
const canonical = [method, rawPath, normalizedQuery, timestamp, nonce, bodyHash].join("\n");
const signature = crypto.createHmac("sha256", secret).update(canonical, "utf8").digest("base64url");
```

仓库同时提供 Java 测试类 [`MerchantOrderSignatureGeneratorTest.java`](../platform-service/src/test/java/com/example/payments/platform/service/client/MerchantOrderSignatureGeneratorTest.java)，可生成完整请求参数和签名测试向量。将测试类中的测试凭证替换为本地凭证后运行：

```bash
cd payment-acquiring-backend
mvn -pl platform-service -Dtest=MerchantOrderSignatureGeneratorTest test
```

测试输出中的 `body` 必须作为实际 HTTP 请求体原样发送；输出的 `headers` 可直接映射为请求头。测试类中的 `Idempotency-Key` 仅用于幂等控制，不参与签名原文。

下单请求的顶层字段固定为：`merchantOrderNo`、`appId`、`payModel`、`country`、`currency`、`amount`、`expireAt`、`notifyUrl`、`returnUrl`、`customerReference`、`payoutDestinationRef`、`description`、`payer`、`channelParams`。以后新增渠道只能使用 `channelParams` 承载渠道专属参数，不新增顶层字段；公共字段的含义和类型保持不变。`appId` 是商户产品绑定的公开自增标识，平台内部再解析为产品配置。

## 创建订单

`POST /api/v1/payments/orders`

```json
{
  "merchantOrderNo": "M202409070001",
  "appId": "1000",
  "payModel": "CARD",
  "country": "US",
  "currency": "USD",
  "amount": 100.00,
  "notifyUrl": "https://merchant.example.com/payment/notify",
  "returnUrl": "https://merchant.example.com/payment/return",
  "description": "订单说明",
  "payer": {
    "userId": "user-001",
    "email": "payer@example.com"
  },
  "channelParams": {
    "billingAddress": "可选的渠道参数"
  }
}
```

`payer` 用于付款人通用资料，字段固定为 `userId`、`name`、`firstName`、`lastName`、`phone`、`email`。`channelParams` 是对象，键名和取值由已发布的渠道配置约束；不得在其中放置密钥、签名、卡号或其他敏感认证材料。

字段约束：`merchantOrderNo` 最长 128 个字符，`appId` 为后台商户产品列表展示的从 `1000` 开始递增的数字字符串（示例 `1000` 仅用于说明，必须替换为当前商户实际 App ID），`payModel` 最长 64 个字符，`country` 为两位国家代码，`currency` 为三位 ISO 4217 代码，`amount` 最多 4 个小数位且大于 0，`description` 最长 1000 个字符。`expireAt`、`notifyUrl`、`returnUrl`、`customerReference`、`payoutDestinationRef` 均为可选公共字段。

响应返回 `orderId`、金额、币种、订单状态和过期时间。创建成功不代表支付成功，商户应继续查询订单或等待异步通知。

## 查询订单

`GET /api/v1/payments/orders/{orderId}`

`GET /api/v1/payments/orders/{orderId}/status`

订单状态包括 `CREATED`、`PROCESSING`、`SUCCESS`、`FAILED`、`CANCELED`、`EXPIRED`。只有 `SUCCESS` 可作为收款完成依据。

## 取消订单

`POST /api/v1/payments/orders/{orderId}/cancel`

仅允许取消未进入终态的订单。已成功、已失败、已取消或已过期订单不会被重复改变状态。

## 异步通知

平台向创建订单时提供的 `notifyUrl` 投递订单状态通知。商户必须校验签名、按 `eventId` 幂等处理，并返回 HTTP 2xx。通知失败会按平台策略重试；商户仍应通过订单查询接口进行最终确认。

## 退款

退款接口见 `POST /api/v1/payments/refunds`、`GET /api/v1/payments/refunds/{refundId}` 及对应的回调接口。退款只能针对 `SUCCESS` 订单发起，支持全额和部分退款，商户需要提供唯一退款幂等键。

## 错误处理

`400` 表示参数或幂等键缺失，`401/403` 表示认证或权限失败，`404` 表示资源不存在或不属于当前商户，`409` 表示幂等键冲突或状态冲突，`422` 表示业务校验失败，`500` 表示平台内部错误。发生可重试错误时，请使用原幂等键重试。

## 推荐接入顺序

创建订单 -> 根据订单返回信息完成收款交互 -> 接收并验签异步通知 -> 查询订单确认最终状态 -> 按需发起退款。
