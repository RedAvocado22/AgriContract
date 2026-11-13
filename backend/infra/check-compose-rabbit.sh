#!/usr/bin/env bash
# Every Java service with a @RabbitListener that runs in docker-compose.yml must get RABBITMQ_HOST, otherwise it
# falls back to localhost and silently never consumes (#428: product-service missed contract.* events).
# Run from anywhere: bash backend/infra/check-compose-rabbit.sh
set -euo pipefail

BACKEND="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE="$BACKEND/docker-compose.yml"
status=0

# Lines of one top-level service block: from "  <name>:" to the next line indented exactly two spaces.
service_block() {
  awk -v svc="  $1:" '
    $0 == svc { inside = 1; next }
    inside && /^  [^ #]/ { exit }
    inside { print }
  ' "$COMPOSE"
}

for dir in "$BACKEND"/*-service; do
  name="$(basename "$dir")"
  [ -d "$dir/src/main/java" ] || continue
  grep -rqs "@RabbitListener" "$dir/src/main/java" || continue
  block="$(service_block "$name")"
  if [ -z "$block" ]; then
    echo "SKIP  $name: has RabbitMQ consumers but is not in docker-compose.yml"
    continue
  fi
  if grep -q "RABBITMQ_HOST:" <<<"$block" && grep -q "^      rabbitmq:" <<<"$block"; then
    echo "OK    $name"
  else
    echo "FAIL  $name: needs RABBITMQ_HOST and depends_on rabbitmq in docker-compose.yml"
    status=1
  fi
done
exit $status
