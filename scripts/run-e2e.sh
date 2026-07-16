#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

resolve_container() {
  local candidate
  for candidate in "$@"; do
    if docker inspect -f '{{.State.Running}}' "$candidate" >/dev/null 2>&1; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  return 1
}

resolve_container_ip() {
  local container="$1"
  docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$container"
}

DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-password}"
BRUNO_BASE_URL="${BRUNO_BASE_URL:-}"

PRODUCT_DB_CONTAINER="$(resolve_container agricontract-e2e-product-db product-db)"
CONTRACT_DB_CONTAINER="$(resolve_container agricontract-e2e-contract-db contract-db)"
ESCROW_DB_CONTAINER="$(resolve_container agricontract-e2e-escrow-db escrow-db)"
CONTRACT_SERVICE_CONTAINER="$(resolve_container agricontract-e2e-contract-service contract-service)"
CONTRACT_SERVICE_URL="http://$(resolve_container_ip "$CONTRACT_SERVICE_CONTAINER"):8083"

if [ -z "$BRUNO_BASE_URL" ]; then
  if docker inspect -f '{{.State.Running}}' agricontract-e2e-api-gateway >/dev/null 2>&1; then
    BRUNO_BASE_URL="http://localhost:18080"
  else
    BRUNO_BASE_URL="http://localhost:8080"
  fi
fi

if docker inspect -f '{{.State.Running}}' agricontract-e2e-rabbitmq >/dev/null 2>&1; then
  RABBITMQ_MANAGEMENT_URL="${RABBITMQ_MANAGEMENT_URL:-http://localhost:15673}"
else
  RABBITMQ_MANAGEMENT_URL="${RABBITMQ_MANAGEMENT_URL:-http://localhost:15672}"
fi

echo "==> Resetting seed data..."
docker exec -i "$PRODUCT_DB_CONTAINER"  mysql -uroot -p"$DB_ROOT_PASSWORD" product_db  < "$SCRIPT_DIR/seed/01-product-db.sql"
docker exec -i "$CONTRACT_DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PASSWORD" contract_db < "$SCRIPT_DIR/seed/03-contract-db.sql"
docker exec -i "$ESCROW_DB_CONTAINER"   mysql -uroot -p"$DB_ROOT_PASSWORD" escrow_db   < "$SCRIPT_DIR/seed/04-escrow-db.sql"

echo "==> Resetting mutable seed contract/escrow states..."
docker exec -i "$CONTRACT_DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PASSWORD" contract_db -e "
  UPDATE contracts SET status = 'ACTIVE', cancel_reason = NULL, cancelled_by = NULL
  WHERE contract_id = 'seed-ctr-active';
  UPDATE contracts SET status = 'ACTIVE', cancel_reason = NULL, cancelled_by = NULL
  WHERE contract_id = 'seed-ctr-active-2';
  UPDATE contracts SET status = 'DELIVERED'
  WHERE contract_id = 'seed-ctr-delivered';
  UPDATE contracts SET status = 'OFFERED'
  WHERE contract_id = 'seed-ctr-offered';
"
docker exec -i "$ESCROW_DB_CONTAINER" mysql -uroot -p"$DB_ROOT_PASSWORD" escrow_db -e "
  UPDATE escrow_accounts SET status = 'FULLY_LOCKED'
  WHERE escrow_id = 'seed-esc-active';
  UPDATE escrow_accounts SET status = 'FULLY_LOCKED'
  WHERE escrow_id = 'seed-esc-active-2';
  UPDATE escrow_accounts SET status = 'DISPUTED'
  WHERE escrow_id = 'seed-esc-disputed';
  DELETE FROM escrow_transactions
  WHERE escrow_account_id = (SELECT id FROM (SELECT id FROM escrow_accounts WHERE escrow_id = 'seed-esc-disputed') t)
    AND transaction_type IN ('ARBITRATION_BUYER', 'ARBITRATION_SELLER');
"

echo "==> Running Bruno e2e..."
cd "$ROOT_DIR/bruno"
bru run contract/ --env local -r \
  --env-var base_url="$BRUNO_BASE_URL" \
  --env-var rabbitmq_management_url="$RABBITMQ_MANAGEMENT_URL" \
  --env-var contract_service_url="$CONTRACT_SERVICE_URL"
