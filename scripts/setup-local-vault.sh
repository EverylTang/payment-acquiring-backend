#!/usr/bin/env bash
set -euo pipefail

vault_container="${VAULT_CONTAINER:-payment-vault}"
vault_image="${VAULT_IMAGE:-hashicorp/vault:1.17.6}"
vault_token="${VAULT_DEV_ROOT_TOKEN:-dev-root-token}"
signing_secret="${VAULT_SIMULATED_CHANNEL_SECRET:-payment-local-simulated-signing-secret}"

if ! docker inspect "$vault_container" >/dev/null 2>&1; then
  docker run -d --name "$vault_container" --restart unless-stopped --cap-add=IPC_LOCK -p 127.0.0.1:8200:8200 \
    -e VAULT_DEV_ROOT_TOKEN_ID="$vault_token" \
    -e VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200 \
    "$vault_image" server -dev >/dev/null
elif [ "$(docker inspect --format '{{.State.Running}}' "$vault_container")" != "true" ]; then
  docker start "$vault_container" >/dev/null
fi

for _ in $(seq 1 30); do
  if curl -fsS --max-time 2 http://127.0.0.1:8200/v1/sys/health >/dev/null; then
    break
  fi
  sleep 1
done
curl -fsS --max-time 2 http://127.0.0.1:8200/v1/sys/health >/dev/null

docker exec \
  -e VAULT_ADDR=http://127.0.0.1:8200 \
  -e VAULT_TOKEN="$vault_token" \
  "$vault_container" vault kv put secret/payments/channels/simulated \
  requestSigningKey="$signing_secret" \
  callbackVerifyKey="$signing_secret" >/dev/null

printf 'Vault is ready at http://127.0.0.1:8200. The simulated channel secrets are configured.\n'
