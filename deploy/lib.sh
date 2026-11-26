#!/usr/bin/env bash
# Shared helpers for the deploy scripts.

# Exports KEY=VALUE pairs from a .env file WITHOUT executing it as shell code
# (values may contain spaces, '*', '!' or '$' - e.g. DEMO_RESET_CRON=0 3 * * *).
load_env() {
  local file="${1:-.env}" line key val
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line//[[:space:]]/}" || "$line" =~ ^[[:space:]]*# ]] && continue
    key="${line%%=*}"
    val="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    if [[ "$val" =~ ^\"(.*)\"$ || "$val" =~ ^\'(.*)\'$ ]]; then
      val="${BASH_REMATCH[1]}"
    fi
    export "$key=$val"
  done < "$file"
}
