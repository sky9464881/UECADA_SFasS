#!/usr/bin/env bash
# scripts/up-all.sh
# ----------------------------------------------------------------------
# 3개 라인 (LINE-01/02/03) + 통합 DAS 를 한 번에 기동.
#
# 동작:
#   1) factory-net docker network 가 없으면 생성
#   2) 각 라인의 Node-RED flow 빌드 (build_flow_das.py --host-mode docker)
#   3) 라인 1, 2, 3 compose up -d --build (각각 .env.line0X)
#   4) 통합 DAS flow 빌드 + integration/docker-compose.yml up -d --build
#
# 사용법:
#   ./scripts/up-all.sh             # 위 전체 시퀀스 실행 (기본 = up)
#   ./scripts/up-all.sh down        # 통합 DAS + 3라인 모두 down
#   ./scripts/up-all.sh logs LINE-02  # 특정 라인 logs -f
# ----------------------------------------------------------------------
set -euo pipefail

cd "$(dirname "$0")/.."   # repo root

CMD="${1:-up}"

ensure_network() {
  if ! docker network inspect factory-net >/dev/null 2>&1; then
    echo "==> creating docker network: factory-net"
    docker network create factory-net
  else
    echo "==> factory-net already exists"
  fi
}

build_line_flow() {
  local line="$1"
  local out_file="nodered/flows_das_${line}.json"
  echo "==> building Node-RED flow for ${line} (docker host-mode)"
  # Save to a line-specific file so the Dockerfile can COPY it as build arg.
  LINE_ID="${line}" python3 nodered/build_flow_das.py \
    --line-id "${line}" \
    --host-mode docker \
    --out "${out_file}"
  if [ ! -f "${out_file}" ]; then
    echo "flow file not created: ${out_file}" >&2
    exit 1
  fi
  echo "    -> ${out_file} ($(wc -c < "${out_file}") bytes)"
}

up_line() {
  local line="$1"
  local env_file
  case "$line" in
    LINE-01) env_file=".env.line01" ;;
    LINE-02) env_file=".env.line02" ;;
    LINE-03) env_file=".env.line03" ;;
    *) echo "unknown line: $line"; return 1 ;;
  esac
  build_line_flow "$line"
  echo "==> docker compose --env-file ${env_file} up -d --build"
  docker compose --env-file "${env_file}" up -d --build
}

down_line() {
  local line="$1"
  local env_file
  case "$line" in
    LINE-01) env_file=".env.line01" ;;
    LINE-02) env_file=".env.line02" ;;
    LINE-03) env_file=".env.line03" ;;
    *) echo "unknown line: $line"; return 1 ;;
  esac
  echo "==> docker compose --env-file ${env_file} down"
  docker compose --env-file "${env_file}" down
}

up_integration() {
  echo "==> building integration DAS flow"
  python3 integration/build_flow_integration.py
  echo "==> docker compose -f integration/docker-compose.yml up -d --build"
  docker compose -f integration/docker-compose.yml up -d --build
}

down_integration() {
  echo "==> docker compose -f integration/docker-compose.yml down"
  docker compose -f integration/docker-compose.yml down || true
}

case "$CMD" in
  up|"")
    ensure_network
    for L in LINE-01 LINE-02 LINE-03; do up_line "$L"; done
    up_integration
    echo ""
    echo "==================================================================="
    echo " 기동 완료!"
    echo "   LINE-01 Node-RED UI : http://localhost:2880"
    echo "   LINE-02 Node-RED UI : http://localhost:3880"
    echo "   LINE-03 Node-RED UI : http://localhost:4880"
    echo "   통합 DAS Node-RED UI : http://localhost:5880"
    echo ""
    echo "   LINE-0X DAS OPC UA : opc.tcp://localhost:4860 / 4960 / 5060"
    echo "                          endpoint=line-das/LINE-0X"
    echo "   통합 DAS OPC UA    : opc.tcp://localhost:5860/integration-das"
    echo "==================================================================="
    ;;
  down)
    down_integration
    for L in LINE-01 LINE-02 LINE-03; do down_line "$L" || true; done
    echo "==> NOTE: factory-net 네트워크는 유지됩니다 (수동 삭제: docker network rm factory-net)"
    ;;
  logs)
    LINE="${2:?usage: $0 logs <LINE-0X>}"
    case "$LINE" in
      LINE-01) docker compose --env-file ".env.line01" logs -f ;;
      LINE-02) docker compose --env-file ".env.line02" logs -f ;;
      LINE-03) docker compose --env-file ".env.line03" logs -f ;;
      INTEGRATION|integration|DAS|das) docker compose -f integration/docker-compose.yml logs -f ;;
      *) echo "unknown: $LINE"; exit 1 ;;
    esac
    ;;
  ps|status)
    docker compose --env-file .env.line01 ps
    docker compose --env-file .env.line02 ps
    docker compose --env-file .env.line03 ps
    docker compose -f integration/docker-compose.yml ps
    ;;
  *)
    echo "usage: $0 [up|down|logs <LINE>|ps]"
    exit 1
    ;;
esac
