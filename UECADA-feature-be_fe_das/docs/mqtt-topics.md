# MQTT Topics

## Vibration Window

```text
factory/motor/1/vibration/window
```

## Subscriber

현재 backend는 이 topic을 구독해서 `VibrationWindowMessage` DTO로 변환한 뒤 원본 저장, FastAPI 분석, MySQL 저장까지 수행합니다.

성공 기준:

```text
Received MQTT message: equipmentId=MOTOR_001, windowIndex=0, samplingRate=16000, rpm=1200, windowSize=32000, valuesLength=32000
```

Payload:

```json
{
  "equipmentId": "MOTOR_001",
  "timestamp": "2026-05-06T12:00:00.000Z",
  "samplingRate": 16000,
  "rpm": 1200,
  "windowSize": 32000,
  "windowIndex": 0,
  "values": [-0.13646967, -0.1220464]
}
```

Replay payload에는 정답 라벨을 넣지 않습니다.
