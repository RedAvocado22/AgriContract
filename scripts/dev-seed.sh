#!/usr/bin/env bash
# DEV SEED — chạy: bash scripts/dev-seed.sh
# Đảm bảo Docker stack đang chạy trước (docker compose up -d)
# Idempotent — chạy nhiều lần không bị lỗi (INSERT IGNORE)

set -euo pipefail

DB_PASS="${DB_PASSWORD:-password}"
SEED_DIR="$(cd "$(dirname "$0")/seed" && pwd)"

run_sql() {
  local container="$1" db="$2" file="$3"
  echo "→ Seeding $db..."
  docker exec -i "$container" mysql -uroot -p"${DB_PASS}" "$db" < "$file"
}

run_sql "product-db"  "product_db"  "$SEED_DIR/01-product-db.sql"
run_sql "user-db"     "user_db"     "$SEED_DIR/02-user-db.sql"
run_sql "contract-db" "contract_db" "$SEED_DIR/03-contract-db.sql"
run_sql "escrow-db"   "escrow_db"   "$SEED_DIR/04-escrow-db.sql"

echo ""
echo "Done. Accounts: buyer1@test.com / seller1@test.com"
echo "  product_db : 4 products, 11 listings"
echo "  contract_db: 8 contracts (OFFERED → DISPUTED)"
echo "  escrow_db  : 6 escrow accounts + transactions"
