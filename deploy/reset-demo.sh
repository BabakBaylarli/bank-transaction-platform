#!/usr/bin/env bash
# Resets the public demo to its initial state (run nightly by cron, installed by deploy.sh).
#
#  - empties all business tables (users, accounts, transactions, outbox, processing decisions)
#  - deletes the Elasticsearch read model
#  - restarts transaction-service (re-seeds admin/alice/bob because the users table is empty)
#    and search-service (re-creates the index with its mapping)
#
# Kafka consumer offsets are already committed, so old events are not replayed into the clean state.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env)

# never run two resets at the same time
exec 9>"/tmp/bank-platform-reset.lock"
flock -n 9 || { echo "reset already running"; exit 0; }

# shellcheck source=deploy/lib.sh
. "$ROOT/deploy/lib.sh"
load_env .env

echo "[$(date -Is)] demo reset started"

"${COMPOSE[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-bank}" -d "${POSTGRES_DB:-bank}" <<'SQL'
BEGIN;
TRUNCATE TABLE outbox_events, transactions, accounts, users CASCADE;
TRUNCATE TABLE processing.processed_transactions;
COMMIT;
SQL

"${COMPOSE[@]}" exec -T elasticsearch curl -fsS -X DELETE "http://localhost:9200/transactions?ignore_unavailable=true" > /dev/null

"${COMPOSE[@]}" restart transaction-service search-service

echo "[$(date -Is)] demo reset finished"
