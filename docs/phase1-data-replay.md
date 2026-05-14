# Phase 1: MAT to JSONL to MQTT Replay

This phase proves the first MVP path:

```text
MAT vibration file -> JSONL windows -> Node-RED replay -> MQTT broker
```

## 1. Prepare Python venv on Ubuntu

```bash
bash scripts/setup_ai_api_ubuntu.sh
source ai-api/.venv/bin/activate
python --version
```

## 2. Inspect a MAT file

```bash
python scripts/inspect_mat.py data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_16000/RotatingSpeed_1200/H_H_16_6204_1200.mat
```

Expected fields in this dataset:

```text
Data: raw vibration signal
STFTFreq: spectrogram frequency vector
STFTTime: spectrogram time vector
Spectrogram: precomputed spectrograms
```

For MQTT replay, use only `Data`.

## 3. Convert one MAT file to JSONL

```bash
python scripts/convert_mat_to_jsonl.py \
  --input data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_16000/RotatingSpeed_1200/H_H_16_6204_1200.mat \
  --output data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl \
  --equipment-id MOTOR_001 \
  --window-size 32000 \
  --stride 16000 \
  --max-windows 79 \
  --start-time 2026-05-06T12:00:00Z \
  --decimals 6
```

Default output intentionally does not include fault labels. That JSONL is safe to replay as unknown sensor input.

Only use `--include-label` for offline training or debugging files.

## 4. Replay with Node-RED

You can import this ready-made flow:

```text
node-red/vibration-jsonl-replay-flow.json
```

현재 flow는 설비별 JSONL 파일 3개를 병렬로 읽습니다.

```text
MOTOR_001 -> data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl
MOTOR_002 -> data/jsonl/MOTOR_002_H_IR_16_6204_1200_32000_79.jsonl
MOTOR_003 -> data/jsonl/MOTOR_003_U1_H_16_6204_1200_32000_79.jsonl
```

Ubuntu 로컬 Node-RED 기준 file node 경로 예시:

```text
/home/hwapyeong/smart_factory_vib_monitoring/data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl
/home/hwapyeong/smart_factory_vib_monitoring/data/jsonl/MOTOR_002_H_IR_16_6204_1200_32000_79.jsonl
/home/hwapyeong/smart_factory_vib_monitoring/data/jsonl/MOTOR_003_U1_H_16_6204_1200_32000_79.jsonl
```

Each branch has its own delay node. At 1 msg / 2 sec per branch, the total MQTT stream is about 1.5 msg/sec and each selected motor updates about every 2 seconds.

These replay files match the first model contract:

```text
samplingRate = 16000
windowSize = 32000
stride = 16000
windowSeconds = 2.0
```

Regenerate them with:

```bash
source ai-api/.venv/bin/activate
bash scripts/generate_model_replay_jsonl.sh
```

The flow also includes a local debug reset button:

```text
RESET DB + raw files -> POST http://localhost:8080/api/debug/reset-data
```

This clears `alarm_history`, `analysis_result`, `vibration_window`, and `data/raw_windows/*`. It keeps `equipment`.

For local dashboard performance, the flow is intentionally set to 1 message per 2 seconds per motor. If you increase this rate, also consider increasing the frontend refresh interval or lowering the raw chart `maxPoints`.

The interleaved sample is still available for simple single-file tests:

```text
/home/hwapyeong/smart_factory_vib_monitoring/data/jsonl/PHM_3_MOTORS_8K_1200_2048_200_each_interleaved.jsonl
```

Manual flow:

```text
inject -> file in -> split -> json -> delay -> mqtt out
```

Suggested settings:

| Node | Setting |
| --- | --- |
| file in | `data/jsonl` JSONL file path |
| split | split by `\n` |
| json | convert string to JavaScript object |
| delay | rate limit, for example 1 msg/sec |
| mqtt out | `localhost:1883`, topic `factory/motor/1/vibration/window` |

## 5. Verify with Mosquitto

In a separate terminal:

```bash
mosquitto_sub -h localhost -p 1883 -t factory/motor/1/vibration/window
```

Then click the Node-RED inject button. One JSON line should appear for each vibration window.
