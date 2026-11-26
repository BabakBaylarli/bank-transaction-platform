#!/usr/bin/env bash
# Pulls the latest code, (re)builds and starts the production stack, installs the cron jobs and
# prints the health of every container.
#
#   ./deploy/deploy.sh            # from the repository root on the server
#   SKIP_PULL=1 ./deploy/deploy.sh  # deploy the working copy as it is
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env)
log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

if [[ ! -f .env ]]; then
  echo "Missing .env - run: cp .env.prod.example .env && nano .env" >&2
  exit 1
fi
# shellcheck source=deploy/lib.sh
. "$ROOT/deploy/lib.sh"
load_env .env

for var in DOMAIN ACME_EMAIL JWT_SECRET POSTGRES_PASSWORD KIBANA_PASSWORD SEED_ADMIN_PASSWORD SEED_CUSTOMER_PASSWORD; do
  if [[ -z "${!var:-}" ]]; then
    echo "Required variable $var is empty in .env" >&2
    exit 1
  fi
done
if (( ${#JWT_SECRET} < 32 )); then
  echo "JWT_SECRET must be at least 32 characters" >&2
  exit 1
fi

# ------------------------------------------------------------------ code
if [[ -z "${SKIP_PULL:-}" && -d .git ]]; then
  log "Pulling latest code"
  git pull --ff-only
fi

# ------------------------------------------------------------------ build & start
log "Building images (first build on a 2-core VM takes ~10 minutes)"
"${COMPOSE[@]}" build

log "Starting the stack"
"${COMPOSE[@]}" up -d --remove-orphans

# ------------------------------------------------------------------ cron jobs
log "Installing cron jobs"
CRON_TMP="$(mktemp)"
crontab -l 2>/dev/null | grep -v '# bank-platform' > "$CRON_TMP" || true
if [[ "${DEMO_MODE:-true}" == "true" && -n "${DEMO_RESET_CRON:-}" ]]; then
  echo "${DEMO_RESET_CRON} $ROOT/deploy/reset-demo.sh >> $ROOT/deploy/reset-demo.log 2>&1 # bank-platform" >> "$CRON_TMP"
  echo "  demo reset: ${DEMO_RESET_CRON}"
fi
if [[ -n "${DUCKDNS_TOKEN:-}" && "$DOMAIN" == *.duckdns.org ]]; then
  SUB="${DOMAIN%.duckdns.org}"
  echo "*/5 * * * * curl -fsS 'https://www.duckdns.org/update?domains=${SUB}&token=${DUCKDNS_TOKEN}&ip=' > /dev/null # bank-platform" >> "$CRON_TMP"
  echo "  DuckDNS update every 5 minutes for ${SUB}"
fi
crontab "$CRON_TMP"
rm -f "$CRON_TMP"

# ------------------------------------------------------------------ health
log "Waiting for services to become healthy (up to 6 minutes)"
SERVICES=(postgres kafka elasticsearch kibana transaction-service processing-service search-service)
deadline=$((SECONDS + 360))
while (( SECONDS < deadline )); do
  unhealthy=0
  for s in "${SERVICES[@]}"; do
    id="$("${COMPOSE[@]}" ps -q "$s")"
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || echo missing)"
    [[ "$status" == "healthy" ]] || unhealthy=$((unhealthy + 1))
  done
  (( unhealthy == 0 )) && break
  printf '.'
  sleep 10
done
echo
"${COMPOSE[@]}" ps --format 'table {{.Service}}\t{{.Status}}'

URL="${PUBLIC_ORIGIN:-https://${DOMAIN}}"
log "Smoke test against ${URL}"
code="$(curl -ks -o /dev/null -w '%{http_code}' "${URL}/")" || code="000"
echo "  frontend            ${URL}/  -> HTTP ${code}"
code="$(curl -ks -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"'"${SEED_CUSTOMER_PASSWORD}"'"}' "${URL}/api/v1/auth/login")" || code="000"
echo "  login (alice)       ${URL}/api/v1/auth/login -> HTTP ${code}"
code="$(curl -ks -o /dev/null -w '%{http_code}' "${URL}/kibana/")" || code="000"
echo "  kibana (no auth)    ${URL}/kibana/ -> HTTP ${code} (401 expected)"

docker image prune -f > /dev/null
log "Deployed: ${URL}"
