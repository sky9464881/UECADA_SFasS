# 설비/라인 상세 팝업 API 명세

이 문서는 FE 팝업 화면에서 필요한 데이터만 모아 정리한 명세다.  
현재 설비 상세보기와 라인 상세보기 팝업은 DB 저장값이 아니라 Spring Boot가 보유한 실시간 버퍼를 우선 조회하며, FE는 2초마다 다시 요청한다.

## 공통 규칙

- 기본 API Host: `http://localhost:8080`
- 실시간 갱신 주기: 2초
- 버퍼 key 형식: `{LINE}.{EQUIPMENT}:{metric}`
  - 예: `LINE01.CAST01:sensor_current`
  - 예: `LINE03.TEST02:result_ok`
- `LINE-01_CAST-01` 같은 설비 코드는 FE에서 `LINE01.CAST01` 버퍼 prefix로 변환한다.

## 1. 설비 상세보기 팝업

### 1.1 설비 목록

```http
GET /api/equipments?factoryId=FACTORY-01
```

주요 응답 필드:

| 필드 | 설명 |
|---|---|
| `equipmentCode` | 설비 코드. 예: `LINE-01_CAST-01` |
| `equipmentName` | 설비명 |
| `processType` | `주조`, `가공`, `세척`, `조립`, `검사` |
| `location` | 라인 ID. 예: `LINE-01` |
| `model` | 설비 모델 |

### 1.2 설비 상태

```http
GET /api/equipment-status?equipIds=LINE-01_CAST-01,LINE-01_CNC-01
```

주요 응답 필드:

| 필드 | 설명 |
|---|---|
| `equipId` | 설비 코드 |
| `statusCode` | `RUNNING`, `STANDBY`, `ALARM`, `MAINTENANCE` |
| `updatedAt` | 상태 갱신 시각 |

### 1.3 공통 상세내역 버퍼

```http
POST /api/sensors/latest-values
Content-Type: application/json

{
  "bufferKeys": [
    "LINE01.CAST01:sensor_current",
    "LINE01.CAST01:sensor_voltage",
    "LINE01.CAST01:sensor_temperature",
    "LINE01.CAST01:sensor_vibration",
    "LINE01.CAST01:cycle_time"
  ]
}
```

공통 상세내역:

| 화면 표시 | 버퍼 metric | 단위 |
|---|---|---|
| 전류 | `sensor_current` | A |
| 전압 | `sensor_voltage` | V |
| 온도 | `sensor_temperature` | ℃ |
| 진동 | `sensor_vibration` | a.u. |
| 싸이클 타임 | `cycle_time` | s |

응답:

| 필드 | 설명 |
|---|---|
| `bufferKey` | 요청한 버퍼 key |
| `size` | 버퍼에 쌓인 frame 수 |
| `capacity` | 버퍼 최대 크기 |
| `latest.timestampMs` | 최신 frame 시각 |
| `latest.value` | 최신 값 |

### 1.4 설비별 Type Data

설비 상세보기의 Type Data는 아래 3개 항목만 표시한다. 진동 분석 결과는 Type Data에 넣지 않는다.

| 설비 유형 | 버퍼 metric | 화면 단위 |
|---|---|---|
| 주조기 | `injection_pressure` | MPa |
| 주조기 | `mold_temperature` | ℃ |
| 주조기 | `cooling_flow` | L/min |
| 가공기 | `spindle_speed` | rpm |
| 가공기 | `tool_usage` | % |
| 가공기 | `coolant_flow` | L/min |
| 세척기 | `cleaning_concentration` | % |
| 세척기 | `cleaning_temperature` | ℃ |
| 세척기 | `cleaning_pressure` | bar |
| 조립기 | `tightening_torque` | Nm |
| 조립기 | `tightening_angle` | deg |
| 조립기 | `press_force` | N |
| 검사기 | `bore_dimension` | mm |
| 검사기 | `hole_dimension` | mm |
| 검사기 | `result_ok` | bool |

예시:

```http
POST /api/sensors/latest-values

{
  "bufferKeys": [
    "LINE01.CAST01:injection_pressure",
    "LINE01.CAST01:mold_temperature",
    "LINE01.CAST01:cooling_flow"
  ]
}
```

### 1.5 진동 원본/FFT/분석값

```http
GET /api/vibration/realtime/{equipmentCode}
```

예:

```http
GET /api/vibration/realtime/LINE-01_CAST-01
```

주요 응답 필드:

| 필드 | 설명 |
|---|---|
| `received` | 실시간 진동 window 수신 여부 |
| `equipmentId` | 설비 코드 |
| `receivedAt` | window 수신 시각 |
| `window.samplingRate` | 샘플링 레이트 |
| `window.windowSize` | window sample 수 |
| `values` | 원본 진동 window 배열 |
| `analysis.features.rms` | RMS |
| `analysis.features.peakToPeak` | Peak-to-Peak |
| `analysis.features.crestFactor` | Crest Factor |
| `analysis.features.kurtosis` | Kurtosis |
| `analysis.fft.frequencies` | FFT 주파수 배열 |
| `analysis.fft.magnitudes` | FFT magnitude 배열 |
| `analysis.prediction` | AI 예측 후보 |
| `analysis.confidence` | 예측 신뢰도 |
| `analysis.alarmLevel` | `normal`, `warning`, `danger` |

## 2. 라인 상세보기 팝업

라인 상세보기는 왼쪽 메뉴 화면이 아니라 `라인별 현황` 화면에서 라인을 클릭할 때 팝업으로 열린다.

### 2.1 팝업 URL

```text
/#/layout?lineId=LINE-01&popup=1
```

### 2.2 라인 요약

```http
GET /api/lines?factoryId=FACTORY-01
```

주요 응답 필드:

| 필드 | 설명 |
|---|---|
| `lineId` | `LINE-01`, `LINE-02`, `LINE-03` |
| `lineName` | 라인명 |
| `lineStatus` | 라인 상태 |
| `equipmentTotal` | 라인 설비 수 |
| `equipmentRunning` | 가동 설비 수 |
| `equipmentAlarm` | 알람 설비 수 |
| `equipmentStandby` | 대기 설비 수 |
| `equipmentMaintenance` | 보전 설비 수 |
| `openAlarmCount` | 열린 알람 수 |
| `latestOee` | 최신 OEE |

### 2.3 라인 설비 흐름과 라인밸런싱

라인 팝업은 아래 API들을 조합한다.

| 용도 | API |
|---|---|
| 라인별 설비 구성 | `GET /api/equipments?factoryId=FACTORY-01` |
| 설비별 상태 | `GET /api/equipment-status?equipIds=...` |
| 설비별 cycle/온도 | `POST /api/sensors/latest-values` |

라인 팝업에서 사용하는 실시간 버퍼:

| 화면 영역 | 버퍼 metric |
|---|---|
| 공정별 CT | `cycle_time` |
| 공정별 온도 표시 | `sensor_temperature` |

라인밸런싱 계산:

| 값 | 계산 |
|---|---|
| 공정 평균 CT | 같은 공정 설비의 `cycle_time` 평균 |
| 라인밸런싱 | `min(공정 평균 CT) / max(공정 평균 CT) * 100` |
| UPH | `3600 / 병목 공정 CT` |
| UPMH | `UPH * 가동 설비 수` |
| 생산성 | 라인 OEE 기반 표시 |

## 3. 메인 대시보드

메인 대시보드는 아래 API를 2초마다 호출한다.

```http
GET /api/dashboard/frontend
```

Spring Boot는 이 응답을 만들 때 `LineAggregationService`와 `VibrationWindowMonitorService`를 통해 현재 BE 버퍼와 실시간 알람 상태를 반영한다.

주요 응답 필드:

| 필드 | 설명 |
|---|---|
| `factoryOee` | 전체 OEE |
| `statusDonut.running` | 가동 설비 수 |
| `statusDonut.standby` | 대기 설비 수 |
| `statusDonut.alarm` | 알람 설비 수 |
| `statusDonut.maintenance` | 보전 설비 수 |
| `alarmSummary` | 실시간 알람 요약 |
| `lineStats` | 라인별 OEE |
| `oeeHourlySeries` | 라인별 OEE 추세 표시용 데이터 |
