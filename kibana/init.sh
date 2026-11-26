#!/bin/sh
# Imports the "Transactions Overview" dashboard (data view + visualizations) into Kibana.
# Runs once as the `kibana-init` compose service; safe to re-run (overwrite=true).
set -eu

KIBANA_URL="${KIBANA_URL:-http://localhost:5601}"
FILE="$(dirname "$0")/dashboard.ndjson"

echo "Waiting for Kibana at ${KIBANA_URL} ..."
until curl -fs "${KIBANA_URL}/api/status" | grep -q '"level":"available"'; do
  sleep 5
done

echo "Importing saved objects from ${FILE}"
response="$(curl -fsS -X POST "${KIBANA_URL}/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form "file=@${FILE}")"
echo "${response}"

echo "${response}" | grep -q '"success":true' || { echo "Import failed"; exit 1; }
echo "Dashboard ready: ${KIBANA_URL}/app/dashboards#/view/transactions-overview"
