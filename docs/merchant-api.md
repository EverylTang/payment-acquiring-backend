# 商户收单 API

本文档描述商户可直接调用的订单、通知和退款接口。订单是唯一的收单业务对象，订单状态以查询接口和验签后的异步通知为准。

## 认证与通用约定

所有接口需要请求头 `X-Merchant-Id`、`X-Gateway-Token`。创建订单必须提供唯一的 `Idempotency-Key`。请求和响应使用 JSON，金额使用十进制定点数，币种使用 ISO 4217 代码。

## 创建订单

`POST /api/v1/payments/orders`

```json
{
  "merchantOrderNo": "M202409070001",
  "productCode": "CARD_PAYIN",
  "paymentMethod": "CARD",
  "country": "US",
  "currency": "USD",
  "amount": 100.00,
  "notifyUrl": "https://merchant.example.com/payment/notify",
  "returnUrl": "https://merchant.example.com/payment/return",
  "description": "订单说明"
}
```

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
