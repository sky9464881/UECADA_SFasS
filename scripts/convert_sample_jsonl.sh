#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PYTHON_BIN="${PYTHON_BIN:-$ROOT_DIR/ai-api/.venv/bin/python}"
INPUT="${INPUT:-data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_8000/RotatingSpeed_1200/H_H_8_6204_1200.mat}"
OUTPUT="${OUTPUT:-data/jsonl/MOTOR_001_H_H_8_6204_1200_2048_5_check.jsonl}"
EQUIPMENT_ID="${EQUIPMENT_ID:-MOTOR_001}"
WINDOW_SIZE="${WINDOW_SIZE:-2048}"
MAX_WINDOWS="${MAX_WINDOWS:-5}"
START_TIME="${START_TIME:-2026-05-06T12:00:00Z}"

if [[ ! -x "$PYTHON_BIN" ]]; then
  echo "Python not found: $PYTHON_BIN" >&2
  echo "Run: bash scripts/setup_ai_api_ubuntu.sh" >&2
  exit 1
fi

"$PYTHON_BIN" scripts/convert_mat_to_jsonl.py \
  --input "$INPUT" \
  --output "$OUTPUT" \
  --equipment-id "$EQUIPMENT_ID" \
  --window-size "$WINDOW_SIZE" \
  --max-windows "$MAX_WINDOWS" \
  --start-time "$START_TIME"

wc -l "$OUTPUT"
"$PYTHON_BIN" - "$OUTPUT" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]
first = rows[0]

print("rows:", len(rows))
print("keys:", sorted(first.keys()))
print("samplingRate:", first["samplingRate"])
print("rpm:", first["rpm"])
print("windowSize:", first["windowSize"])
print("values length:", len(first["values"]))
print("label exists:", "label" in first)
print("output:", path)
PY
