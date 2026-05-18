#!/usr/bin/env bash
# Usage:
#   ./scripts/up.sh LINE-01
#   ./scripts/up.sh LINE-02 logs -f
#   ./scripts/up.sh LINE-03 down
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <LINE-01|LINE-02|LINE-03> [docker compose args...]"
  exit 1
fi

LINE="$1"
shift

case "$LINE" in
  LINE-01) ENV_FILE=".env.line01" ;;
  LINE-02) ENV_FILE=".env.line02" ;;
  LINE-03) ENV_FILE=".env.line03" ;;
  *) echo "unknown line: $LINE"; exit 1 ;;
esac

FLOW_FILE="nodered/flows_das_${LINE}.json"
if [[ ! -f "$FLOW_FILE" ]]; then
  echo "missing fixed line DAS flow file: $FLOW_FILE" >&2
  exit 1
fi

if [[ $# -eq 0 ]]; then
  echo "==> using fixed Node-RED flow for ${LINE}: ${FLOW_FILE}"
  set -- up -d --build
fi

docker network inspect total-das-net >/dev/null 2>&1 || docker network create total-das-net >/dev/null

echo "==> docker compose --env-file $ENV_FILE $*"
exec docker compose --env-file "$ENV_FILE" "$@"
