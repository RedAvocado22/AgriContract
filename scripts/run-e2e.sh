#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "==> Resetting seed data..."
docker exec -i product-db  mysql -uroot -ppassword product_db  < "$SCRIPT_DIR/seed/01-product-db.sql"
docker exec -i contract-db mysql -uroot -ppassword contract_db < "$SCRIPT_DIR/seed/03-contract-db.sql"
docker exec -i escrow-db   mysql -uroot -ppassword escrow_db   < "$SCRIPT_DIR/seed/04-escrow-db.sql"

echo "==> Resetting mutable seed contract/escrow states..."
docker exec -i contract-db mysql -uroot -ppassword contract_db -e "
  UPDATE contracts SET status = 'ACTIVE', cancel_reason = NULL, cancelled_by = NULL
  WHERE contract_id = 'seed-ctr-active';
"
docker exec -i escrow-db mysql -uroot -ppassword escrow_db -e "
  UPDATE escrow_accounts SET status = 'FULLY_LOCKED'
  WHERE escrow_id = 'seed-esc-active';
"

echo "==> Running Bruno e2e..."
cd "$ROOT_DIR/bruno"
bru run contract/ --env local -r
