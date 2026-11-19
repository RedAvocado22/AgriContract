#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "==> Resetting seed data..."
docker exec -i user-db     mysql --default-character-set=utf8mb4 -uroot -ppassword user_db     < "$SCRIPT_DIR/seed/02-user-db.sql"
docker exec -i product-db  mysql --default-character-set=utf8mb4 -uroot -ppassword product_db  < "$SCRIPT_DIR/seed/01-product-db.sql"
# contract_db / escrow_db have no seed (#450): the Bruno run creates its own contract (06-create-contract).

echo "==> Running Bruno e2e..."
cd "$ROOT_DIR/bruno"
bru run contract/ --env local -r
