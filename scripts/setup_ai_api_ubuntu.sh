#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/ai-api"

PYTHON_VERSION="3.12.10"
EXPECTED_PYTHON_VERSION="Python ${PYTHON_VERSION}"
UV_BIN="${UV_BIN:-}"

if [[ -z "$UV_BIN" ]]; then
  if command -v uv >/dev/null 2>&1; then
    UV_BIN="$(command -v uv)"
  elif [[ -x "$HOME/.local/bin/uv" ]]; then
    UV_BIN="$HOME/.local/bin/uv"
  fi
fi

if [[ -n "$UV_BIN" ]]; then
  "$UV_BIN" python install "$PYTHON_VERSION"
  "$UV_BIN" venv --python "$PYTHON_VERSION" --seed --clear .venv
else
  ACTUAL_PYTHON_VERSION="$(python3.12 --version)"
  if [[ "$ACTUAL_PYTHON_VERSION" != "$EXPECTED_PYTHON_VERSION" ]]; then
    echo "Expected $EXPECTED_PYTHON_VERSION, but got $ACTUAL_PYTHON_VERSION" >&2
    echo "Install uv or Python $PYTHON_VERSION first, then rerun this script." >&2
    exit 1
  fi
  python3.12 -m venv .venv
fi

source .venv/bin/activate
python --version
python -m pip install --upgrade pip
python -m pip install -r requirements.lock.txt
python -m pip check

echo "AI API venv is ready: $ROOT_DIR/ai-api/.venv"
