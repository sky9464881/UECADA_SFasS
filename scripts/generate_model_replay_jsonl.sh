#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

python scripts/convert_mat_to_jsonl.py \
  --input data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_16000/RotatingSpeed_1200/H_H_16_6204_1200.mat \
  --output data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl \
  --equipment-id MOTOR_001 \
  --window-size 32000 \
  --stride 16000 \
  --max-windows 79 \
  --start-time 2026-05-06T12:00:00Z \
  --decimals 6

python scripts/convert_mat_to_jsonl.py \
  --input data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_16000/RotatingSpeed_1200/H_IR_16_6204_1200.mat \
  --output data/jsonl/MOTOR_002_H_IR_16_6204_1200_32000_79.jsonl \
  --equipment-id MOTOR_002 \
  --window-size 32000 \
  --stride 16000 \
  --max-windows 79 \
  --start-time 2026-05-06T12:00:00Z \
  --decimals 6

python scripts/convert_mat_to_jsonl.py \
  --input data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_16000/RotatingSpeed_1200/U1_H_16_6204_1200.mat \
  --output data/jsonl/MOTOR_003_U1_H_16_6204_1200_32000_79.jsonl \
  --equipment-id MOTOR_003 \
  --window-size 32000 \
  --stride 16000 \
  --max-windows 79 \
  --start-time 2026-05-06T12:00:00Z \
  --decimals 6
