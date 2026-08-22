#!/usr/bin/env bash
set -euo pipefail

: "${TRADE_BASE_URL:?set TRADE_BASE_URL, e.g. http://127.0.0.1:8082}"
: "${FUND_BASE_URL:?set FUND_BASE_URL, e.g. http://127.0.0.1:8083}"
merchant_id="${E2E_MERCHANT_ID:-merchant-demo}"
order_id="e2e-$(date +%s)"
payload=$(printf '{"merchantId":"%s","merchantOrderNo":"%s","productCode":"CARD-US-USD","paymentMethod":"CARD","country":"US","currency":"USD","amount":10.00}' "$merchant_id" "$order_id")
order=$(curl -fsS -X POST "$TRADE_BASE_URL/api/v1/payments/orders" -H 'Content-Type: application/json' -H "Idempotency-Key: $order_id" -d "$payload")
printf '%s\n' "$order" | rg -q 'orderId|SUCCESS|PAYING'
printf 'payment e2e order accepted: %s\n' "$order_id"
printf 'fund health: '
curl -fsS "$FUND_BASE_URL/actuator/health"
printf '\n'
