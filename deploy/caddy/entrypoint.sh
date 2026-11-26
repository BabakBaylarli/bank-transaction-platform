#!/bin/sh
# Hashes the plain-text Kibana password from .env at start-up, so the operator never has to
# generate a bcrypt hash (and never has to escape '$' characters in .env).
set -eu

if [ -z "${KIBANA_PASSWORD:-}" ]; then
  echo "KIBANA_PASSWORD is not set" >&2
  exit 1
fi
KIBANA_PASSWORD_HASH="$(caddy hash-password --plaintext "$KIBANA_PASSWORD")"
export KIBANA_PASSWORD_HASH
unset KIBANA_PASSWORD

exec caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
