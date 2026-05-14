# API Spec Draft

## Backend REST API

```text
GET /api/equipments
GET /api/equipments/{equipmentCode}/latest
GET /api/equipments/{equipmentCode}/analysis-results
GET /api/alarms
GET /api/alarms/{alarmId}/focus-analysis
GET /api/alarms/{alarmId}/focus-analysis/selection
GET /api/dashboard/summary
```

Alarm rows are event based. A row opens when an equipment changes from `normal` to `warning`/`danger`, then closes when the equipment returns to `normal`. `durationSeconds` records the interval between `occurredAt` and `endedAt`.

Alarm focus analysis is loaded on demand and does not create extra database rows.

```text
GET /api/alarms/{alarmId}/focus-analysis?paddingSeconds=10&maxPoints=40000
GET /api/alarms/{alarmId}/focus-analysis/selection?startMillis=...&endMillis=...&maxSamples=64000
```

The first endpoint reads stored raw window JSON files and existing analysis rows around the alarm interval. The second endpoint analyzes only the user-selected interval and returns FFT/features/AI output without saving the result.

Raw vibration chart endpoint:

```text
GET /api/equipments/{equipmentCode}/vibration-windows/raw-series?limit=5&maxPoints=8000
```

This endpoint returns downsampled chart points. `sampleCount` is the returned display point count. `originalSampleCount` is the raw sample count before downsampling.

## FastAPI

```text
GET  /health
POST /analyze
```

`POST /analyze`는 Spring Boot가 MQTT payload를 전달하는 분석 API입니다.

Request:

```json
{
  "equipmentId": "MOTOR_001",
  "timestamp": "2026-05-06T12:00:00.000Z",
  "samplingRate": 16000,
  "rpm": 1200,
  "windowSize": 32000,
  "windowIndex": 0,
  "values": [-0.13646967, -0.1220464, "..."]
}
```

Response:

```json
{
  "equipmentId": "MOTOR_001",
  "timestamp": "2026-05-06T12:00:00.000Z",
  "samplingRate": 16000,
  "rpm": 1200,
  "windowSize": 32000,
  "windowIndex": 0,
  "features": {
    "rms": 0.0,
    "peakFrequency": 0.0,
    "peakToPeak": 0.0,
    "crestFactor": 0.0,
    "kurtosis": 0.0
  },
  "fft": {
    "frequencyResolution": 0.5,
    "binCount": 16001,
    "frequencies": [0.0, 3.90625],
    "magnitudes": [0.0, 0.0]
  },
  "anomalyScore": 0.0,
  "alarmLevel": "normal",
  "prediction": "bearing",
  "confidence": 0.87,
  "modelVersion": "spectrogram-pca-rf-v1",
  "modelInputType": "spectrogram",
  "modelInputSize": 32000,
  "modelExpectedInputSize": 4096,
  "modelInputStrategy": "stft_spectrogram_64x64_from_raw",
  "modelStatus": "loaded"
}
```

현재 AI 모델이 없으면 `prediction=not_trained`, `modelStatus=missing`으로 내려옵니다.
실제 `windowSize=32000` payload를 보내면 FFT bin은 `16001`개가 반환됩니다.
