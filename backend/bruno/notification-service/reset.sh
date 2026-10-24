#!/usr/bin/env bash
# Resets the state the notification-service parity run (Bruno 00-19, plan M4)
# depends on, so a Java run and a Nest run start from the same point:
#   - DELETE FROM notification_logs (table and flyway_schema_history kept)
#   - purge the 9 notification-svc.* queues and their 9 DLQs (queues kept)
#   - delete every message in MailHog
# then prints the resulting state and fails if a consumer is still attached.
# Nothing else is touched: no other database, no volume.
#
# Usage (Linux, macOS, Git Bash on Windows):
#   ./reset.sh            reset, then check
#   ./reset.sh --status   only print the current state (no reset, no check)
#
# Env overrides: NOTIFICATION_DB_CONTAINER, DB_ROOT_PASSWORD, RABBITMQ_CONTAINER,
# RABBITMQ_MANAGEMENT_URL, RABBITMQ_USERNAME, RABBITMQ_PASSWORD, MAILHOG_URL.
set -euo pipefail

DB_CONTAINER="${NOTIFICATION_DB_CONTAINER:-notification-db}"
DB_PASSWORD="${DB_ROOT_PASSWORD:-password}"
RABBIT_CONTAINER="${RABBITMQ_CONTAINER:-rabbitmq}"
RABBIT_URL="${RABBITMQ_MANAGEMENT_URL:-http://localhost:15672}"
RABBIT_USER="${RABBITMQ_USERNAME:-guest}"
RABBIT_PASS="${RABBITMQ_PASSWORD:-guest}"
MAILHOG="${MAILHOG_URL:-http://localhost:8025}"

QUEUES=(
  contract.signed contract.delivered contract.cancelled contract.disputed
  escrow.locked escrow.released escrow.penalized
  category.approved category.rejected
)

sql() {
  docker exec "$DB_CONTAINER" mysql -uroot -p"$DB_PASSWORD" notification_db -e "$1" 2>/dev/null
}

status() {
  echo "== notification_logs"
  sql "SELECT COUNT(*) AS rows_total FROM notification_logs;"
  echo "== flyway_schema_history"
  sql "SELECT version, checksum, success FROM flyway_schema_history;"
  echo "== MailHog"
  curl -sf "$MAILHOG/api/v2/messages?limit=1" | grep -o '"total":[0-9]*' || echo "MailHog not reachable"
  echo "== queues (name messages consumers)"
  docker exec "$RABBIT_CONTAINER" rabbitmqctl list_queues -q name messages consumers | grep 'notification-svc\.' | sort
}

if [[ "${1:-}" == "--status" ]]; then
  status
  exit 0
fi

echo "Deleting notification_logs rows"
sql "DELETE FROM notification_logs;"

for key in "${QUEUES[@]}"; do
  for q in "notification-svc.$key" "notification-svc.$key.dlq"; do
    code=$(curl -s -o /dev/null -w '%{http_code}' -u "$RABBIT_USER:$RABBIT_PASS" \
      -X DELETE "$RABBIT_URL/api/queues/%2F/$q/contents")
    if [[ "$code" != "204" ]]; then
      echo "Purge of $q failed (HTTP $code)" >&2
      exit 1
    fi
  done
done
echo "Purged ${#QUEUES[@]} queues and ${#QUEUES[@]} DLQs"

curl -sf -X DELETE "$MAILHOG/api/v1/messages" >/dev/null
echo "Deleted MailHog messages"

status

busy=$(docker exec "$RABBIT_CONTAINER" rabbitmqctl list_queues -q name consumers \
  | awk '$1 ~ /^notification-svc\./ && $2 > 0 { print $1 }')
if [[ -n "$busy" ]]; then
  echo "Consumers still attached to: $busy" >&2
  echo "Stop the notification service before a parity run." >&2
  exit 1
fi
echo "OK: no consumer on notification-svc.* queues"
