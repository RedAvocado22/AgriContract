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
  UPDATE contracts SET status = 'ACTIVE', cancel_reason = NULL, cancelled_by = NULL
  WHERE contract_id = 'seed-ctr-active-2';
  UPDATE contracts SET status = 'DELIVERED'
  WHERE contract_id = 'seed-ctr-delivered';
  UPDATE contracts SET status = 'OFFERED'
  WHERE contract_id = 'seed-ctr-offered';
"
docker exec -i escrow-db mysql -uroot -ppassword escrow_db -e "
  UPDATE escrow_accounts SET status = 'FULLY_LOCKED'
  WHERE escrow_id = 'seed-esc-active';
  UPDATE escrow_accounts SET status = 'FULLY_LOCKED'
  WHERE escrow_id = 'seed-esc-active-2';
  UPDATE escrow_accounts SET status = 'FULLY_LOCKED'
  WHERE escrow_id = 'seed-esc-disputed';
  DELETE FROM escrow_transactions
  WHERE escrow_account_id = (SELECT id FROM (SELECT id FROM escrow_accounts WHERE escrow_id = 'seed-esc-disputed') t)
    AND transaction_type IN ('ARBITRATION_BUYER', 'ARBITRATION_SELLER');
"

echo "==> Running Bruno e2e..."
cd "$ROOT_DIR/bruno"
bru run contract/ --env local -r
