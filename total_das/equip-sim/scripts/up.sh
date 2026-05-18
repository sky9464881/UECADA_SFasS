#!/usr/bin/env bash
# 사용법: ./scripts/up.sh LINE-01 [docker compose 옵션...]
# 예:   ./scripts/up.sh LINE-01 up -d --build
#       ./scripts/up.sh LINE-02 logs -f
#       ./scripts/up.sh LINE-03 down
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <LINE-01|LINE-02|LINE-03> [docker compose args...]"
  exit 1
fi
LINE="$1"; shift
ENV_FILE=".env.${LINE,,}"                # LINE-01 -> .env.line-01 … 안 됨
case "$LINE" in
  LINE-01) ENV_FILE=".env.line01" ;;
  LINE-02) ENV_FILE=".env.line02" ;;
  LINE-03) ENV_FILE=".env.line03" ;;
  *) echo "unknown line: $LINE"; exit 1 ;;
esac

# 기본 동작: build flow + up -d --build
if [[ $# -eq 0 ]]; then
  echo "==> building Node-RED flow for $LINE (docker host-mode)"
  LINE_ID="$LINE" python3 nodered/build_flow_das.py --host-mode docker
  set -- up -d --build
fi

echo "==> docker compose --env-file $ENV_FILE $*"
exec docker compose --env-file "$ENV_FILE" "$@"
