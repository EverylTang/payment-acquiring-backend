#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
exec /opt/homebrew/bin/mvn -pl trade-service spring-boot:run
