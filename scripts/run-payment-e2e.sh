#!/usr/bin/env bash
set -euo pipefail

: "${TRADE_BASE_URL:?set TRADE_BASE_URL, e.g. http://127.0.0.1:8082}"
: "${FUND_BASE_URL:?set FUND_BASE_URL, e.g. http://127.0.0.1:8083}"
merchant_id="${E2E_MERCHANT_ID:-merchant-demo}"
order_id="e2e-$(date +%s)"
payload=$(printf '{"merchantId":"%s","merchantOrderNo":"%s","productCode":"CARD-US-USD","paymentMethod":"CARD","country":"US","currency":"USD","amount":10.00}' "$merchant_id" "$order_id")
order=$(curl -fsS -X POST "$TRADE_BASE_URL/api/v1/payments/orders" -H 'Content-Type: application/json' -H "Idempotency-Key: $order_id" -d "$payload")
created_order_id=$(printf '%s' "$order" | sed -n 's/.*"orderId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
test -n "$created_order_id"
attempt=$(curl -fsS -X POST "$TRADE_BASE_URL/api/v1/payments/orders/$created_order_id/attempts?behavior=SUCCESS")
printf '%s\n' "$attempt" | rg -q 'SUCCESS'
sleep "${E2E_SETTLE_WAIT_SECONDS:-3}"
printf 'payment e2e order accepted and attempt succeeded: %s\n' "$created_order_id"
if command -v docker >/dev/null && docker inspect "${MYSQL_CONTAINER:-local-mysql}" >/dev/null 2>&1; then
  mysql_password="${MYSQL_ROOT_PASSWORD:-root123456}"
  outbox_status=$(docker exec "${MYSQL_CONTAINER:-local-mysql}" mysql -uroot "-p${mysql_password}" -Nse "SELECT COUNT(*) FROM pay_trade.payment_outbox_event WHERE aggregate_id='${created_order_id}' AND event_type='PAYMENT_SUCCEEDED'")
  ledger_count=$(docker exec "${MYSQL_CONTAINER:-local-mysql}" mysql -uroot "-p${mysql_password}" -Nse "SELECT COUNT(*) FROM pay_fund.ledger_entry WHERE order_id='${created_order_id}' AND entry_type='PAYMENT_SUCCESS'")
  test "$outbox_status" -ge 1
  test "$ledger_count" -eq 1
  printf 'outbox and fund ledger verified: %s\n' "$created_order_id"
fi
printf 'fund health: '
curl -fsS "$FUND_BASE_URL/actuator/health"
printf '\n'
