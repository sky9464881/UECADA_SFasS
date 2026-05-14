# Node-RED

Node-RED는 실제 센서 대신 JSONL vibration window를 한 줄씩 MQTT로 발행하는 DAS/Edge Gateway 역할을 합니다.

현재 import용 flow:

```text
vibration-jsonl-replay-flow.json
```

로컬 MQTT topic:

```text
factory/motor/1/vibration/window
```

Docker Compose에서 Node-RED를 실행할 때는 JSONL 파일 경로를 `/project-data/jsonl/...` 형태로 맞추면 됩니다.

현재 flow는 설비별 JSONL 파일 3개를 병렬로 읽습니다.

```text
MOTOR_001 -> data/jsonl/MOTOR_001_H_H_16_6204_1200_32000_79.jsonl
MOTOR_002 -> data/jsonl/MOTOR_002_H_IR_16_6204_1200_32000_79.jsonl
MOTOR_003 -> data/jsonl/MOTOR_003_U1_H_16_6204_1200_32000_79.jsonl
```

각 브랜치 delay가 1 msg / 2 sec이므로 전체 MQTT 유입은 약 1.5 msg/sec이고, 각 설비는 약 2초마다 갱신됩니다.

각 payload는 `samplingRate=16000`, `windowSize=32000`입니다. 2초 window를 1초 stride로 자른 모델 1차 기준 replay 파일입니다.

디버깅용 reset 버튼도 포함되어 있습니다.

```text
RESET DB + raw files
```

이 버튼은 Spring Boot local profile에서만 열리는 아래 API를 호출합니다.

```text
POST http://localhost:8080/api/debug/reset-data
```

삭제 대상:

```text
alarm_history
analysis_result
vibration_window
data/raw_windows/*
```

`equipment` 테이블은 유지합니다.

참고용 interleaved 샘플:

```text
/project-data/jsonl/PHM_3_MOTORS_8K_1200_2048_200_each_interleaved.jsonl
```
