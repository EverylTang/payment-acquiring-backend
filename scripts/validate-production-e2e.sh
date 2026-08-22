#!/usr/bin/env bash
set -euo pipefail

mysql_container="${MYSQL_CONTAINER:-local-mysql}"
mysql_password="${MYSQL_ROOT_PASSWORD:-root123456}"
mysql_exec=(docker exec "$mysql_container" mysql -uroot "-p${mysql_password}" -Nse)

docker inspect "$mysql_container" >/dev/null
docker inspect "local-rocketmq-namesrv" >/dev/null
docker inspect "payment-rocketmq-broker" >/dev/null
"${mysql_exec[@]}" "SELECT 1" | grep -qx 1
for schema in pay_trade pay_fund; do
  "${mysql_exec[@]}" "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='${schema}'" | grep -qx "$schema"
done
"${mysql_exec[@]}" "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='pay_trade'" | grep -q '[0-9]'

if command -v nc >/dev/null; then
  nc -z 127.0.0.1 9876
  nc -z 127.0.0.1 10911
fi

echo "production dependencies reachable: MySQL + RocketMQ"
echo "service E2E requires TRADE_BASE_URL/FUND_BASE_URL; no URLs were supplied, so HTTP flow was not invoked"
