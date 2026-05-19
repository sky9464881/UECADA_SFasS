# SWMP 팝업 API 명세서

이 문서는 SWMP(SMWP)에서 UECADA의 설비 상세보기 팝업과 라인 상세보기 팝업을 연동할 때 필요한 전체 API 명세다.
연동 방식은 두 가지를 모두 지원한다.

- **팝업 URL 연동**: SWMP 화면에서 UECADA Vue 화면을 iframe 또는 새 창으로 띄운다.
- **API 직접 연동**: SWMP가 Spring Boot API를 직접 호출해서 동일한 팝업 UI를 자체 구현한다.

현재 실시간 화면은 DB 저장값보다 **Spring Boot 메모리 버퍼**를 우선 사용한다. DAS/X_DAS에서 들어오는 센서 버퍼는 약 2초 주기로 갱신되고, FE도 실시간 화면에서 2초 주기로 다시 조회한다.

## 1. 기본 정보

| 항목 | 값 |
| --- | --- |
| Frontend host | `http://localhost:5173` 또는 `http://127.0.0.1:5173` |
| Backend API host | `http://localhost:8080` |
| Hash router | Vue는 `/#/...` 형식의 URL을 사용한다. |
| 기본 factoryId | `FACTORY-01` |
| 실시간 센서 갱신 주기 | 2초 |
| 알람 갱신 주기 | 1초 |
| HTTP timeout | 30초 |
| JSON header | `Content-Type: application/json` |

브라우저에서 SWMP가 Spring Boot API를 직접 호출하는 경우 CORS는 현재 `http://localhost:*`, `http://127.0.0.1:*`만 허용한다. SWMP가 다른 도메인/포트에서 뜨면 `backend/src/main/java/com/example/phm/common/CorsConfig.java`의 `allowedOriginPatterns`에 SWMP origin을 추가해야 한다.

## 2. 인증과 세션

Spring Boot API는 현재 대부분의 팝업 조회 API에서 별도 토큰 검증을 강제하지 않는다. 다만 UECADA Vue 화면은 라우터 가드가 있어서 FE 팝업 URL을 직접 열 때는 UECADA 로그인 세션이 필요하다.

로그인 API:

```http
POST /api/auth/login
Content-Type: application/json

{
  "loginId": "admin",
  "password": "secret"
}
```

응답:

```json
{
  "userId": "U001",
  "loginId": "admin",
  "userName": "관리자",
  "email": "admin@uecada.com",
  "roleName": "ADMIN",
  "lineId": null
}
```

FE는 로그인 성공 시 `sessionStorage`에 데모 토큰을 저장한다.

| sessionStorage key | 용도 |
| --- | --- |
| `uecada_access_token` | FE 인증 여부 판단용 데모 토큰 |
| `uecada_role` | `admin`, `manager`, `operator` |
| `uecada_user` | 로그인 사용자 JSON |

초기 계정의 비밀번호와 보안질문 답변은 모두 `secret`이다.

| 역할 | 라인 | 로그인 ID |
| --- | --- | --- |
| 전체 관리자 | 전체 | `admin` |
| LINE-01 관리자 | LINE-01 | `line01_manager` |
| LINE-01 작업자 | LINE-01 | `line01_operator` |
| LINE-02 관리자 | LINE-02 | `line02_manager` |
| LINE-02 작업자 | LINE-02 | `line02_operator` |
| LINE-03 관리자 | LINE-03 | `line03_manager` |
| LINE-03 작업자 | LINE-03 | `line03_operator` |

## 3. 팝업 URL 연동

SWMP에서 가장 단순하게 붙이는 방식이다. SWMP가 라인 또는 설비 ID만 알고 있으면 Vue 화면을 그대로 띄울 수 있다.

### 3.1 설비 상세보기 팝업

```text
http://localhost:5173/#/equipment?equipmentId={equipmentCode}&popup=1
```

예시:

```text
http://localhost:5173/#/equipment?equipmentId=LINE-01_CAST-01&popup=1
```

iframe 예시:

```html
<iframe
  src="http://localhost:5173/#/equipment?equipmentId=LINE-01_CAST-01&popup=1"
  style="width: 1280px; height: 820px; border: 0;"
></iframe>
```

새 창 예시:

```js
window.open(
  'http://localhost:5173/#/equipment?equipmentId=LINE-01_CAST-01&popup=1',
  'uecada-equipment-LINE-01_CAST-01',
  'width=1320,height=860'
)
```

### 3.2 라인 상세보기 팝업

라인 상세보기는 왼쪽 메뉴의 별도 페이지가 아니라 `라인별 현황` 화면에서 라인을 클릭할 때 떠야 하는 팝업이다.

```text
http://localhost:5173/#/layout?lineId={lineId}&popup=1
```

예시:

```text
http://localhost:5173/#/layout?lineId=LINE-01&popup=1
```

iframe 예시:

```html
<iframe
  src="http://localhost:5173/#/layout?lineId=LINE-01&popup=1"
  style="width: 1280px; height: 820px; border: 0;"
></iframe>
```

### 3.3 팝업 URL 생성용 SWMP Helper API

SWMP가 URL 문자열을 직접 만들지 않고 백엔드에서 받을 수도 있다.

```http
GET /api/swmp/equipment/{equipmentCode}
GET /api/swmp/lines/{lineId}
```

응답의 `popupUrl`은 Vue hash router 기준의 상대 경로다.

```json
{
  "equipmentCode": "LINE-01_CAST-01",
  "popupUrl": "/#/equipment?equipmentId=LINE-01_CAST-01&popup=1"
}
```

SWMP에서는 host를 붙여 사용한다.

```js
const feHost = 'http://localhost:5173'
const popupUrl = feHost + response.popupUrl
```

## 4. ID와 버퍼 Key 규칙

### 4.1 라인 ID

| 라인 ID | 의미 |
| --- | --- |
| `LINE-01` | 1라인 |
| `LINE-02` | 2라인 |
| `LINE-03` | 3라인 |

### 4.2 설비 코드

설비 코드는 `라인_설비` 형식이다.

| 예시 | 의미 |
| --- | --- |
| `LINE-01_CAST-01` | LINE-01의 주조기 1번 |
| `LINE-01_CNC-01` | LINE-01의 가공기 1번 |
| `LINE-01_WASH-01` | LINE-01의 세척기 1번 |
| `LINE-01_ASSY-01` | LINE-01의 조립기 1번 |
| `LINE-01_TEST-01` | LINE-01의 검사기 1번 |

실제 설비 목록은 반드시 아래 API로 조회한다.

```http
GET /api/equipments?factoryId=FACTORY-01
```

### 4.3 실시간 버퍼 Key

Spring Boot의 실시간 센서 버퍼 key는 다음 형식이다.

```text
{LINE}.{EQUIPMENT}:{metric}
```

변환 규칙:

| 설비 코드 | 버퍼 prefix |
| --- | --- |
| `LINE-01_CAST-01` | `LINE01.CAST01` |
| `LINE-02_CNC-03` | `LINE02.CNC03` |
| `LINE-03_TEST-02` | `LINE03.TEST02` |

예시:

```text
LINE01.CAST01:sensor_current
LINE02.CNC03:cycle_time
LINE03.TEST02:result_ok
```

LINE-01은 기존 화면 호환을 위해 `CAST01:sensor_current` 같은 라인 없는 alias도 일부 지원하지만, SWMP 신규 연동은 반드시 `LINE01.CAST01:...` 형식의 line-scoped key를 사용한다.

## 5. 실시간 metric 전체 목록

### 5.1 공통 상세내역 metric

모든 설비 상세보기 팝업에서 공통으로 보여야 하는 값이다.

| 화면 항목 | metric | 단위 | 설명 |
| --- | --- | --- | --- |
| 싸이클 타임 | `cycle_time` | s | 설비/PLC에서 올라오는 cycle time |
| 전류 | `sensor_current` | A | DAS 공통 센서 전류 |
| 전압 | `sensor_voltage` | V | DAS 공통 센서 전압 |
| 온도 | `sensor_temperature` | ℃ | DAS 공통 센서 온도 |
| 진동 | `sensor_vibration` | a.u. | DAS 공통 진동 요약값. 원본 window가 아니라 scalar 값이다. |

### 5.2 설비별 Type Data metric

설비 상세보기의 Type Data에는 아래 3개 값만 표시한다. 진동 분석 결과는 Type Data에 넣지 않는다.

| 설비 유형 | processType | metric | 화면 라벨 | 단위 |
| --- | --- | --- | --- | --- |
| 주조기 | `주조` | `injection_pressure` | injection_pressure | MPa |
| 주조기 | `주조` | `mold_temperature` | mold_temperature | ℃ |
| 주조기 | `주조` | `cooling_flow` | cooling_flow | L/min |
| 가공기 | `가공` | `spindle_speed` | spindle_speed | rpm |
| 가공기 | `가공` | `tool_usage` | tool_usage | % |
| 가공기 | `가공` | `coolant_flow` | coolant_flow | L/min |
| 세척기 | `세척` | `cleaning_concentration` | cleaning_concentration | % |
| 세척기 | `세척` | `cleaning_temperature` | cleaning_temperature | ℃ |
| 세척기 | `세척` | `cleaning_pressure` | cleaning_pressure | bar |
| 조립기 | `조립` | `tightening_torque` | tightening_torque | Nm |
| 조립기 | `조립` | `tightening_angle` | tightening_angle | deg |
| 조립기 | `조립` | `press_force` | press_force | N |
| 검사기 | `검사` | `bore_dimension` | bore_dimension | mm |
| 검사기 | `검사` | `hole_dimension` | hole_dimension | mm |
| 검사기 | `검사` | `result_ok` | result_ok | bool |

### 5.3 호환/운영 metric

백엔드는 기존 DAS/PLC 호환을 위해 아래 metric도 인식한다. 신규 팝업에서는 5.1, 5.2의 metric을 우선 사용한다.

| metric | 설명 |
| --- | --- |
| `temperature` | legacy 온도 |
| `pressure` | legacy 압력 |
| `spindle_load` | legacy 가공 부하 |
| `spindle_rpm` | legacy 가공 rpm |
| `feed_rate` | legacy feed rate |
| `water_temp` | legacy 세척수 온도 |
| `flow_rate` | legacy 유량 |
| `torque` | legacy 조립 토크 |
| `leak_pressure` | legacy 검사 압력 |

## 6. SWMP 연동 빠른 절차

### 6.1 UECADA 팝업을 그대로 띄우는 경우

1. `GET /health`로 백엔드 실행 상태를 확인한다.
2. `GET /api/equipments?factoryId=FACTORY-01` 또는 `GET /api/lines?factoryId=FACTORY-01`로 ID를 조회한다.
3. SWMP에서 iframe 또는 새 창으로 팝업 URL을 연다.
4. 팝업 화면이 로그인 페이지로 이동하면 UECADA에 먼저 로그인한다.

### 6.2 SWMP가 설비 상세 팝업을 직접 구현하는 경우

필수 호출:

| 순서 | API | 용도 |
| --- | --- | --- |
| 1 | `GET /api/equipments?factoryId=FACTORY-01` | 설비명, processType, 라인 위치 |
| 2 | `GET /api/equipment-status?equipIds={equipmentCode}` | 운전상태 |
| 3 | `POST /api/sensors/latest-values` | 전류, 전압, 온도, 진동, cycle, Type Data |
| 4 | `GET /api/vibration/realtime/{equipmentCode}` | 원본 진동 window, FFT, AI 예측, 최신 분석 feature |
| 5 | `GET /api/equipments/{equipmentCode}/analysis-results?limit=80` | 구간 특징값 흐름 |
| 6 | `GET /api/alarms?equipmentCode={equipmentCode}&status=OPEN` | 열린 알람 |

선택 호출:

| API | 용도 |
| --- | --- |
| `GET /api/swmp/equipment/{equipmentCode}` | 설비 metadata, 센서 최신값, 진동 최신값을 한 번에 받는 helper |
| `GET /api/equipments/{equipmentCode}/vibration-windows/raw-series?limit=5&maxPoints=8000` | DB 저장 raw window를 여러 개 이어 붙인 원본 차트 |
| `GET /api/equipments/{equipmentCode}/vibration-windows/latest/raw` | DB에 저장된 최신 raw window |
| `GET /api/operation-logs?...` | 설비 운전 이력 |

### 6.3 SWMP가 라인 상세 팝업을 직접 구현하는 경우

필수 호출:

| 순서 | API | 용도 |
| --- | --- | --- |
| 1 | `GET /api/lines?factoryId=FACTORY-01` | 라인 OEE, 설비 수, 알람 수 |
| 2 | `GET /api/equipments?factoryId=FACTORY-01` | 라인별 설비 구성 |
| 3 | `GET /api/equipment-status?equipIds=...` | 설비별 상태 |
| 4 | `POST /api/sensors/latest-values` | 설비별 cycle time, 온도 |
| 5 | `GET /api/alarms?status=OPEN` | 전체/라인 알람 집계 |

선택 호출:

| API | 용도 |
| --- | --- |
| `GET /api/swmp/lines/{lineId}` | 라인 요약과 popupUrl helper |
| `GET /api/dashboard/frontend` | 전체 OEE, 전체 상태 분포, OEE 추세 |

### 6.4 전체 Endpoint Index

SWMP 팝업과 운영 화면에서 사용할 수 있는 Spring Boot API 전체 목록이다. `필수`는 팝업 직접 구현에 필요한 API, `보조`는 상세/이력/운영 화면에서 함께 쓰는 API, `주의`는 개발/초기화용 API다.

| 구분 | Method | Path | 용도 | SWMP 사용 |
| --- | --- | --- | --- | --- |
| Health | `GET` | `/health` | 백엔드 생존 확인 | 필수 점검 |
| Health | `GET` | `/` | 서비스 정보 | 보조 |
| Health | `GET` | `/api/database/status` | DB 연결 확인 | 보조 |
| Pipeline | `GET` | `/api/pipeline/status` | DAS/X_DAS/BE 버퍼 수신 상태 확인 | 필수 점검 |
| SWMP | `GET` | `/api/swmp/equipment/{equipmentCode}` | 설비 팝업 helper | 필수 또는 보조 |
| SWMP | `GET` | `/api/swmp/lines/{lineId}` | 라인 팝업 helper | 필수 또는 보조 |
| 기준정보 | `GET` | `/api/equipments` | 설비 목록 | 필수 |
| 기준정보 | `GET` | `/api/equipment-status` | 설비 상태 조회 | 필수 |
| 기준정보 | `PUT` | `/api/equipment-status/{equipId}` | 설비 상태 수동 수정 | 보조 |
| 기준정보 | `GET` | `/api/lines` | 라인 목록/집계 | 필수 |
| 센서 | `POST` | `/api/sensors/latest-values` | 여러 버퍼 최신값 조회 | 필수 |
| 센서 | `GET` | `/api/sensors/latest-values` | 여러 버퍼 최신값 조회 fallback | 보조 |
| 센서 | `GET` | `/api/sensors` | 등록된 버퍼 key 목록 | 보조 |
| 센서 | `GET` | `/api/sensors/{bufferKey}` | 특정 버퍼 snapshot | 보조 |
| 센서 | `POST` | `/api/sensors/{bufferKey}` | 특정 버퍼 frame push | 주의 |
| 진동 | `GET` | `/api/vibration/realtime/{equipmentCode}` | 설비별 실시간 raw/FFT/AI | 필수 |
| 진동 | `GET` | `/api/vibration/realtime` | 전체 설비 실시간 진동 | 보조 |
| 진동 | `GET` | `/api/vibration/latest` | 마지막 수신 window 요약 | 보조 |
| 진동 DB | `GET` | `/api/equipments/{equipmentCode}/vibration-windows/latest/raw` | DB 최신 raw window | 보조 |
| 진동 DB | `GET` | `/api/equipments/{equipmentCode}/vibration-windows/raw-series` | DB raw window series | 보조 |
| 분석 | `GET` | `/api/equipments/{equipmentCode}/analysis-results` | 설비별 분석 이력 | 필수 |
| 분석 | `POST` | `/api/analysis-results` | 분석 결과 수동 저장 | 주의 |
| 알람 | `GET` | `/api/alarms` | 알람 조회 | 필수 |
| 알람 | `POST` | `/api/alarms` | 알람 수동 생성 | 보조/주의 |
| 알람 | `PATCH` | `/api/alarms/{alarmId}/resolve` | 알람 처리 완료 | 보조 |
| 알람 | `GET` | `/api/alarms/stats` | 알람 통계 | 보조 |
| 알람 이력 | `GET` | `/api/alarm-histories` | 알람 이력 | 보조 |
| 알람 이력 | `GET` | `/api/alarm-histories/{alarmId}/focus-analysis` | 알람 구간 raw/분석 | 보조 |
| 알람 이력 | `GET` | `/api/alarm-histories/{alarmId}/focus-analysis/selection` | 선택 구간 재분석 | 보조 |
| 대시보드 | `GET` | `/api/dashboard/frontend` | FE 대시보드 요약 | 보조 |
| 대시보드 | `GET` | `/api/dashboard/summary` | 운영 요약 | 보조 |
| 운전 로그 | `GET` | `/api/operation-logs` | 설비 운전 이력 | 보조 |
| 운전 로그 | `POST` | `/api/operation-logs` | 설비 운전 이력 저장 | 보조 |
| 인증 | `POST` | `/api/auth/login` | 로그인 | 보조 |
| 인증 | `POST` | `/api/auth/signup` | 회원가입 | 보조 |
| 인증 | `POST` | `/api/auth/find-id` | 아이디 찾기 | 보조 |
| 인증 | `GET` | `/api/auth/security-question` | 보안 질문 조회 | 보조 |
| 인증 | `POST` | `/api/auth/reset-password` | 비밀번호 재설정 | 보조 |
| 사용자 | `GET` | `/api/users` | 사용자 목록 | 보조 |
| 사용자 | `POST` | `/api/users` | 사용자 생성 | 보조 |
| 사용자 | `PATCH` | `/api/users/{userId}/role` | 권한/라인 변경 | 보조 |
| 커뮤니티 | `GET` | `/api/community/line-groups` | 라인 그룹 | 보조 |
| 커뮤니티 | `GET` | `/api/posts` | 게시글 목록 | 보조 |
| 커뮤니티 | `POST` | `/api/posts` | 게시글 작성 | 보조 |
| 커뮤니티 | `GET` | `/api/community/chat/rooms` | 채팅방 목록 | 보조 |
| 커뮤니티 | `POST` | `/api/community/chat/rooms/direct` | 1:1 채팅방 생성 | 보조 |
| 커뮤니티 | `GET` | `/api/community/chat/rooms/{roomId}/messages` | 메시지 조회 | 보조 |
| 커뮤니티 | `POST` | `/api/community/chat/rooms/{roomId}/messages` | 메시지 전송 | 보조 |
| 커뮤니티 | `GET` | `/api/community/factory-report` | 자동 문서화 | 보조 |
| Debug | `POST` | `/api/debug/reset-data` | 수집/분석/알람 데이터 초기화 | 주의 |

## 7. API 상세 명세

### 7.1 Health

#### `GET /health`

백엔드 생존 확인.

응답:

```json
{
  "status": "ok"
}
```

#### `GET /`

백엔드 서비스 정보와 주요 endpoint hint.

응답:

```json
{
  "service": "smart-factory-phm-backend",
  "status": "running",
  "endpoints": {
    "health": "/health",
    "databaseStatus": "/api/database/status",
    "latestVibrationWindow": "/api/vibration/latest"
  }
}
```

#### `GET /api/database/status`

DB 연결 상태 확인.

응답:

```json
{
  "connected": true,
  "database": "smart_factory",
  "equipmentCount": 24
}
```

#### `POST /api/debug/reset-data`

로컬/도커 profile에서만 열리는 개발용 초기화 API다. SWMP 운영 화면에서는 호출하지 않는다. 테스트 중 수집 데이터, 분석 결과, 알람, raw window 파일을 비우고 다시 수집 상태를 확인할 때만 사용한다.

```http
POST /api/debug/reset-data
```

응답:

```json
{
  "deletedAlarmRows": 12,
  "deletedAnalysisRows": 340,
  "deletedVibrationWindowRows": 340,
  "deletedRawWindowFiles": 0
}
```

#### `GET /api/pipeline/status`

DAS/X_DAS에서 Spring Boot 버퍼까지 데이터가 올라오는지 확인하는 운영 점검 API.

응답 필드:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `checkedAt` | string | 확인 시각 |
| `sensorBufferKeys` | string[] | 현재 등록된 센서 버퍼 key 목록 |
| `realtimeVibrationEquipmentCount` | number | 최신 진동 window가 있는 설비 수 |
| `realtimeVibrationWindows` | VibrationRealtimeResponse[] | 설비별 최신 진동 window |

정상 기준:

- `sensorBufferKeys`에 `LINE01.CAST01:sensor_current` 같은 key가 보여야 한다.
- `realtimeVibrationEquipmentCount`가 1 이상이어야 진동 window가 들어온 것이다.
- 각 window의 `received`가 `true`이고 `receivedAt`이 최근 시각이어야 한다.

### 7.2 SWMP Helper

#### `GET /api/swmp/equipment/{equipmentCode}`

설비 상세 팝업 연동을 위한 helper API. 설비 metadata, 최신 센서값, 최신 진동 분석값, 팝업 URL을 한 번에 준다.

예시:

```http
GET /api/swmp/equipment/LINE-01_CAST-01
```

응답:

```json
{
  "equipmentCode": "LINE-01_CAST-01",
  "equipment": {
    "id": 1,
    "equipmentCode": "LINE-01_CAST-01",
    "equipmentName": "주조기 1",
    "processType": "주조",
    "model": "CAST-MODEL",
    "installDate": "2024-01-01",
    "location": "LINE-01",
    "locationX": 120.0,
    "locationY": 80.0,
    "createdAt": "2026-05-15T12:00:00"
  },
  "sensors": {
    "sensor_current": { "timestampMs": 1778832000000, "value": 47.2 },
    "sensor_voltage": { "timestampMs": 1778832000000, "value": 220.4 },
    "sensor_temperature": { "timestampMs": 1778832000000, "value": 33.8 },
    "sensor_vibration": { "timestampMs": 1778832000000, "value": 1.918 },
    "cycle_time": { "timestampMs": 1778832000000, "value": 61.0 }
  },
  "vibration": {
    "received": true,
    "equipmentId": "LINE-01_CAST-01",
    "receivedAt": "2026-05-15T03:20:00Z",
    "window": {
      "equipmentId": "LINE-01_CAST-01",
      "timestamp": "2026-05-15T12:20:00",
      "samplingRate": 16000,
      "rpm": 1600,
      "windowSize": 32000,
      "windowIndex": 79,
      "valuesLength": 32000
    },
    "values": [0.012, -0.021, 0.034],
    "analysis": {
      "features": {
        "rms": 0.1516,
        "peakFrequency": 100,
        "peakToPeak": 0.68975,
        "crestFactor": 2.392,
        "kurtosis": 2.338
      },
      "fft": {
        "frequencyResolution": 0.5,
        "binCount": 16000,
        "frequencies": [0, 0.5, 1.0],
        "magnitudes": [0.001, 0.002, 0.003]
      },
      "anomalyScore": 0.34,
      "alarmLevel": "normal",
      "prediction": "normal",
      "confidence": 0.85,
      "modelVersion": "spectrogram_pca_rf_v2",
      "modelStatus": "loaded"
    }
  },
  "popupUrl": "/#/equipment?equipmentId=LINE-01_CAST-01&popup=1"
}
```

주의:

- 이 helper 응답에는 `equipmentStatus.statusCode`가 없다. 운전상태가 필요하면 `GET /api/equipment-status`를 같이 호출한다.
- `sensors`는 최신값만 담는다. 그래프용 과거 시계열은 `GET /api/sensors/{bufferKey}?last=N` 또는 진동 전용 API를 사용한다.

#### `GET /api/swmp/lines/{lineId}`

라인 상세 팝업 연동 helper API.

예시:

```http
GET /api/swmp/lines/LINE-01
```

응답:

```json
{
  "lineId": "LINE-01",
  "line": {
    "lineId": "LINE-01",
    "lineName": "Line A",
    "lineStatus": "RUNNING",
    "factoryId": "FACTORY-01",
    "equipmentTotal": 8,
    "equipmentRunning": 7,
    "equipmentAlarm": 1,
    "equipmentStandby": 0,
    "equipmentMaintenance": 0,
    "openAlarmCount": 1,
    "latestOee": 92.4
  },
  "popupUrl": "/#/layout?lineId=LINE-01&popup=1"
}
```

주의:

- 이 helper는 라인의 설비 노드 목록까지 포함하지 않는다.
- 라인 팝업을 직접 그릴 때는 `GET /api/equipments`, `GET /api/equipment-status`, `POST /api/sensors/latest-values`를 조합한다.

### 7.3 설비/라인 기준정보

#### `GET /api/equipments`

설비 목록 조회.

Query:

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `factoryId` | 아니오 | 보통 `FACTORY-01` 사용 |

예시:

```http
GET /api/equipments?factoryId=FACTORY-01
```

응답 item:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | number | DB PK |
| `equipmentCode` | string | 설비 코드 |
| `equipmentName` | string | 설비명 |
| `processType` | string | `주조`, `가공`, `세척`, `조립`, `검사` |
| `model` | string \| null | 모델명 |
| `installDate` | string \| null | 설치일 |
| `location` | string \| null | 라인 ID |
| `locationX` | number \| null | 레이아웃 X 좌표 |
| `locationY` | number \| null | 레이아웃 Y 좌표 |
| `createdAt` | string \| null | 생성시각 |

#### `GET /api/equipment-status`

설비 상태 조회. 쉼표 구분 또는 같은 파라미터 반복을 모두 사용할 수 있다.

```http
GET /api/equipment-status?equipIds=LINE-01_CAST-01,LINE-01_CNC-01
```

응답 item:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `equipId` | string | 설비 코드 |
| `statusCode` | string | `RUNNING`, `STANDBY`, `ALARM`, `MAINTENANCE` |
| `updatedAt` | string \| null | 상태 저장 시각 |

상태 산정 우선순위:

1. 최신 실시간 진동 분석의 `alarmLevel`이 `warning` 또는 `danger`이면 `ALARM`.
2. 센서 기반 override가 있으면 override 상태 사용.
3. DB의 최신 분석 결과가 `warning` 또는 `danger`이면 `ALARM`.
4. 없으면 DB 상태값, DB 상태도 없으면 `RUNNING`.

#### `PUT /api/equipment-status/{equipId}`

설비 상태를 수동 저장/수정한다. 팝업 조회에는 필수가 아니지만 운영 도구에서 사용할 수 있다.

```http
PUT /api/equipment-status/LINE-01_CAST-01
Content-Type: application/json

{
  "statusCode": "STANDBY"
}
```

#### `GET /api/lines`

라인 목록과 라인별 집계 조회.

```http
GET /api/lines?factoryId=FACTORY-01
```

응답 item:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `lineId` | string | `LINE-01` 등 |
| `lineName` | string | 라인 표시명 |
| `lineStatus` | string | 라인 상태 |
| `factoryId` | string | 공장 ID |
| `equipmentTotal` | number | 라인 설비 전체 수 |
| `equipmentRunning` | number | 가동 설비 수 |
| `equipmentAlarm` | number | 알람 설비 수 |
| `equipmentStandby` | number | 대기 설비 수 |
| `equipmentMaintenance` | number | 보전 설비 수 |
| `openAlarmCount` | number | 미처리 알람 수 |
| `latestOee` | number \| null | 최신 OEE |

### 7.4 센서 버퍼 API

#### `POST /api/sensors/latest-values`

여러 버퍼의 최신값만 한 번에 조회한다. 설비/라인 팝업의 숫자 카드와 공통 상세내역은 이 API를 2초마다 호출한다.

요청:

```json
{
  "bufferKeys": [
    "LINE01.CAST01:sensor_current",
    "LINE01.CAST01:sensor_voltage",
    "LINE01.CAST01:sensor_temperature",
    "LINE01.CAST01:sensor_vibration",
    "LINE01.CAST01:cycle_time",
    "LINE01.CAST01:injection_pressure",
    "LINE01.CAST01:mold_temperature",
    "LINE01.CAST01:cooling_flow"
  ]
}
```

응답 item:

```json
{
  "bufferKey": "LINE01.CAST01:sensor_current",
  "size": 125,
  "capacity": 600,
  "latest": {
    "timestampMs": 1778832000000,
    "value": 47.2
  }
}
```

응답 필드:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `bufferKey` | string | 요청한 버퍼 key |
| `size` | number | 현재 버퍼에 쌓인 frame 수 |
| `capacity` | number | ring buffer 최대 frame 수 |
| `latest` | object \| null | 최신 frame. 버퍼가 없거나 값이 없으면 `null` |
| `latest.timestampMs` | number | epoch milliseconds |
| `latest.value` | number | 최신 센서값 |

#### `GET /api/sensors/latest-values`

GET 방식 fallback. POST가 막힌 환경에서 사용한다.

```http
GET /api/sensors/latest-values?bufferKeys=LINE01.CAST01:sensor_current&bufferKeys=LINE01.CAST01:sensor_voltage
```

또는 comma 방식:

```http
GET /api/sensors/latest-values?bufferKeys=LINE01.CAST01:sensor_current,LINE01.CAST01:sensor_voltage
```

#### `GET /api/sensors`

현재 등록된 전체 버퍼 key 목록 조회.

```http
GET /api/sensors
```

응답:

```json
[
  "LINE01.CAST01:sensor_current",
  "LINE01.CAST01:sensor_voltage"
]
```

#### `GET /api/sensors/{bufferKey}`

특정 버퍼의 snapshot 조회. 디버깅 또는 소형 시계열 차트에 사용한다.

```http
GET /api/sensors/LINE01.CAST01:sensor_current?last=20
```

응답:

```json
{
  "bufferKey": "LINE01.CAST01:sensor_current",
  "size": 125,
  "capacity": 600,
  "latest": { "timestampMs": 1778832000000, "value": 47.2 },
  "frames": [
    { "timestampMs": 1778831996000, "value": 47.1 },
    { "timestampMs": 1778831998000, "value": 47.3 }
  ]
}
```

#### `POST /api/sensors/{bufferKey}`

버퍼에 frame을 직접 push하는 API. 일반 SWMP 조회에서는 사용하지 않는다. DAS/X_DAS 또는 테스트 도구용이다.

```http
POST /api/sensors/LINE01.CAST01:sensor_current
Content-Type: application/json

{
  "frames": [
    { "timestampMs": 1778832000000, "value": 47.2 }
  ]
}
```

성공 응답은 `204 No Content`.

### 7.5 진동 실시간/FFT API

#### `GET /api/vibration/realtime/{equipmentCode}`

설비 상세보기 팝업의 원본 진동 데이터 window, FFT, AI 예측, 최신 feature 카드에 사용하는 핵심 API.

```http
GET /api/vibration/realtime/LINE-01_CAST-01
```

응답:

```json
{
  "received": true,
  "equipmentId": "LINE-01_CAST-01",
  "receivedAt": "2026-05-15T03:20:00Z",
  "window": {
    "equipmentId": "LINE-01_CAST-01",
    "timestamp": "2026-05-15T12:20:00",
    "samplingRate": 16000,
    "rpm": 1600,
    "windowSize": 32000,
    "windowIndex": 79,
    "valuesLength": 32000
  },
  "values": [0.012, -0.021, 0.034],
  "analysis": {
    "analysisResultId": 6752,
    "vibrationWindowId": 927,
    "rawWindowSaved": true,
    "alarmCreated": false,
    "equipmentId": "LINE-01_CAST-01",
    "timestamp": "2026-05-15T12:20:00",
    "samplingRate": 16000,
    "rpm": 1600,
    "windowSize": 32000,
    "windowIndex": 79,
    "features": {
      "rms": 0.1516,
      "peakFrequency": 100,
      "peakToPeak": 0.68975,
      "crestFactor": 2.392,
      "kurtosis": 2.338
    },
    "fft": {
      "frequencyResolution": 0.5,
      "binCount": 16000,
      "frequencies": [0, 0.5, 1.0],
      "magnitudes": [0.001, 0.002, 0.003]
    },
    "anomalyScore": 0.34,
    "alarmLevel": "normal",
    "prediction": "normal",
    "confidence": 0.85,
    "modelVersion": "spectrogram_pca_rf_v2",
    "modelInputType": "raw",
    "modelInputSize": 32000,
    "modelExpectedInputSize": 32000,
    "modelInputStrategy": "stft_spectrogram_64x64_from_raw",
    "modelStatus": "loaded"
  }
}
```

응답 필드:

| 필드 | 설명 |
| --- | --- |
| `received` | 해당 설비의 실시간 window 수신 여부 |
| `equipmentId` | 설비 코드 |
| `receivedAt` | Spring Boot가 window를 받은 시각 |
| `window.samplingRate` | Hz |
| `window.rpm` | window 수집 당시 rpm |
| `window.windowSize` | 원본 sample 수 |
| `window.windowIndex` | window 순번 |
| `values` | 원본 진동 sample 배열. ECharts 원본 진동신호 차트에 사용 |
| `analysis.features.rms` | RMS |
| `analysis.features.peakFrequency` | peak frequency |
| `analysis.features.peakToPeak` | peak-to-peak |
| `analysis.features.crestFactor` | crest factor |
| `analysis.features.kurtosis` | kurtosis |
| `analysis.fft.frequencies` | FFT x축 주파수 |
| `analysis.fft.magnitudes` | FFT y축 크기 |
| `analysis.anomalyScore` | 이상 점수 |
| `analysis.alarmLevel` | `normal`, `warning`, `danger` |
| `analysis.prediction` | AI 예측 후보 |
| `analysis.confidence` | 신뢰도. 0~1이면 FE에서 0~100%로 표시 |

`received=false` 응답:

```json
{
  "received": false,
  "equipmentId": "LINE-01_CAST-01",
  "receivedAt": null,
  "window": null,
  "values": [],
  "analysis": null
}
```

이 경우 화면은 `진동 window 수신 대기`로 표시하고, 필요하면 DB raw fallback API를 조회한다.

#### `GET /api/vibration/realtime`

모든 설비의 최신 실시간 진동 window 목록.

```http
GET /api/vibration/realtime
```

응답은 `VibrationRealtimeResponse[]`다. 대시보드/알람 집계의 실시간 진동 상태 점검에 사용할 수 있다.

#### `GET /api/vibration/latest`

백엔드가 마지막으로 받은 진동 window 요약.

```json
{
  "received": true,
  "receivedCount": 6353,
  "lastReceivedAt": "2026-05-15T03:20:00Z",
  "latest": {
    "equipmentId": "LINE-01_CAST-01",
    "timestamp": "2026-05-15T12:20:00",
    "samplingRate": 16000,
    "rpm": 1600,
    "windowSize": 32000,
    "windowIndex": 79,
    "valuesLength": 32000
  }
}
```

### 7.6 DB 저장 raw vibration API

실시간 팝업은 `GET /api/vibration/realtime/{equipmentCode}`를 우선 사용한다. 아래 API는 DB에 저장된 raw window를 조회하는 fallback/이력용이다.

#### `GET /api/equipments/{equipmentCode}/vibration-windows/latest/raw`

DB에 저장된 최신 raw window.

Query:

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `includeValues` | `true` | `false`이면 values를 비워 metadata만 받는다. |

예시:

```http
GET /api/equipments/LINE-01_CAST-01/vibration-windows/latest/raw?includeValues=true
```

응답:

```json
{
  "id": 927,
  "equipmentId": "LINE-01_CAST-01",
  "measuredAt": "2026-05-15T12:20:00",
  "createdAt": "2026-05-15T12:20:01",
  "timestamp": "2026-05-15T12:20:00",
  "samplingRate": 16000,
  "rpm": 1600,
  "windowSize": 32000,
  "windowIndex": 79,
  "values": [0.012, -0.021, 0.034]
}
```

DB에 저장된 window가 없으면 `404 Not Found`.

#### `GET /api/equipments/{equipmentCode}/vibration-windows/raw-series`

여러 raw window를 시간순으로 이어 붙여 차트용 point 배열로 받는다.

Query:

| 이름 | 기본값 | 범위 | 설명 |
| --- | --- | --- | --- |
| `limit` | `5` | 1~20 | 최근 window 개수 |
| `maxPoints` | `8000` | 1000~20000 | 화면 표시용 최대 point 수. 초과 시 min/max downsample |

예시:

```http
GET /api/equipments/LINE-01_CAST-01/vibration-windows/raw-series?limit=5&maxPoints=8000
```

응답:

```json
{
  "equipmentId": "LINE-01_CAST-01",
  "windowCount": 5,
  "sampleCount": 8000,
  "originalSampleCount": 160000,
  "downsampled": true,
  "samplingRate": 16000,
  "firstWindowIndex": 75,
  "lastWindowIndex": 79,
  "points": [
    { "timestamp": 1778832000000.0, "value": 0.012, "windowIndex": 75 }
  ]
}
```

ECharts 원본 신호 차트 데이터 변환:

```js
const seriesData = response.points.map((p) => [p.timestamp, p.value])
```

### 7.7 분석 결과 API

#### `GET /api/equipments/{equipmentCode}/analysis-results`

설비별 분석 결과 이력. 설비 상세보기 팝업의 `구간 특징값 흐름` 차트에 사용한다.

Query:

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `analysisType` | 없음 | 특정 분석 타입만 필터링 |
| `limit` | `100` | 1~500 |

예시:

```http
GET /api/equipments/LINE-01_CAST-01/analysis-results?limit=80
```

응답 item:

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | number | 분석 결과 ID |
| `vibrationWindowId` | number \| null | 연결된 raw window ID |
| `equipmentCode` | string | 설비 코드 |
| `analysisType` | string | 분석 타입 |
| `resultJson` | string \| null | 원본 결과 JSON |
| `measuredAt` | string \| null | window 측정 시각 |
| `rms` | number \| null | RMS |
| `peakFrequency` | number \| null | peak frequency |
| `peakToPeak` | number \| null | peak-to-peak |
| `crestFactor` | number \| null | crest factor |
| `kurtosis` | number \| null | kurtosis |
| `prediction` | string \| null | AI 예측 |
| `confidence` | number \| null | 신뢰도 |
| `modelVersion` | string \| null | 모델 버전 |
| `modelInputType` | string \| null | 모델 입력 타입 |
| `modelInputSize` | number \| null | 실제 입력 크기 |
| `modelExpectedInputSize` | number \| null | 모델 기대 입력 크기 |
| `modelInputStrategy` | string \| null | 입력 변환 전략 |
| `modelStatus` | string \| null | `loaded` 등 |
| `anomalyScore` | number \| null | 이상 점수 |
| `alarmLevel` | string \| null | `normal`, `warning`, `danger` |
| `createdAt` | string \| null | DB 저장 시각 |

차트 매핑:

| 차트 항목 | 필드 |
| --- | --- |
| RMS 라인 | `rms` |
| Peak-to-Peak 라인 | `peakToPeak` |
| 이상 점수 라인 | `anomalyScore` |
| x축 | `measuredAt` 우선, 없으면 `createdAt` |
| window 개수 표시 | 응답 배열 길이 |

#### `POST /api/analysis-results`

분석 결과 수동 저장용. FastAPI 연동 경로에서는 보통 내부에서 처리되므로 SWMP 조회에는 사용하지 않는다.

```http
POST /api/analysis-results
Content-Type: application/json

{
  "equipmentCode": "LINE-01_CAST-01",
  "analysisType": "manual",
  "resultJson": "{\"memo\":\"manual result\"}"
}
```

### 7.8 알람 API

#### `GET /api/alarms`

현재 알람 목록 조회.

Query:

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `status` | 아니오 | `OPEN`, `IN_PROGRESS`, `RESOLVED` |
| `equipmentCode` | 아니오 | 설비 코드 |
| `from` | 아니오 | ISO date-time |
| `to` | 아니오 | ISO date-time |

예시:

```http
GET /api/alarms?status=OPEN&equipmentCode=LINE-01_CAST-01
```

응답 item:

```json
{
  "alarmId": 101,
  "equipmentCode": "LINE-01_CAST-01",
  "alarmType": "진동 이상",
  "severity": "WARNING",
  "status": "OPEN",
  "alarmMessage": "RMS warning threshold exceeded",
  "occurredAt": "2026-05-15T12:20:00",
  "resolvedBy": null,
  "resolvedAt": null,
  "comment": null
}
```

#### `POST /api/alarms`

알람 수동 생성. 일반적으로 FastAPI/분석 저장 흐름에서 자동 생성된다.

```http
POST /api/alarms
Content-Type: application/json

{
  "equipmentCode": "LINE-01_CAST-01",
  "alarmCode": "VIB_WARN",
  "alarmType": "진동 이상",
  "alarmCategory": "vibration",
  "severity": "WARNING",
  "alarmMessage": "RMS warning threshold exceeded",
  "occurredAt": "2026-05-15T12:20:00",
  "sensorSnapshot": "{\"rms\":0.42}"
}
```

#### `PATCH /api/alarms/{alarmId}/resolve`

알람 처리 완료.

```http
PATCH /api/alarms/101/resolve
Content-Type: application/json

{
  "resolvedBy": "U001",
  "resolvedAt": "2026-05-15T12:30:00",
  "comment": "점검 완료"
}
```

#### `GET /api/alarms/stats`

기간별 알람 통계.

```http
GET /api/alarms/stats?from=2026-05-15T00:00:00&to=2026-05-16T00:00:00
```

응답 item:

```json
{
  "date": "2026-05-15",
  "alarmType": "진동 이상",
  "count": 3
}
```

#### `GET /api/alarm-histories`

알람 이력 목록. 알람 상세/이력 화면에서 사용한다.

```http
GET /api/alarm-histories?limit=100
```

응답 item:

| 필드 | 설명 |
| --- | --- |
| `id` | 알람 이력 ID |
| `equipmentCode` | 설비 코드 |
| `analysisResultId` | 연결 분석 ID |
| `alarmLevel` | `warning`, `danger` 등 |
| `status` | 이력 상태 |
| `message` | 메시지 |
| `occurredAt` | 발생 시각 |
| `endedAt` | 종료 시각 |
| `durationSeconds` | 지속 시간 |
| `anomalyScore`, `rms`, `peakToPeak`, `kurtosis`, `prediction` | 발생 당시 분석값 |

#### `GET /api/alarm-histories/{alarmId}/focus-analysis`

알람 발생 구간 주변 raw vibration과 분석 trend를 조회한다.

Query:

| 이름 | 기본값 | 설명 |
| --- | --- | --- |
| `paddingSeconds` | `10` | 알람 전후 포함 시간 |
| `maxPoints` | `40000` | raw point 최대 수 |

응답 주요 필드:

| 필드 | 설명 |
| --- | --- |
| `points` | raw vibration point 배열 |
| `analysisTrend` | 알람 구간 feature trend |
| `rangeStart`, `rangeEnd` | 조회 범위 |
| `samplingRate` | 샘플링 주파수 |
| `downsampled` | downsample 여부 |

#### `GET /api/alarm-histories/{alarmId}/focus-analysis/selection`

알람 상세 차트에서 사용자가 선택한 구간만 다시 분석한다.

```http
GET /api/alarm-histories/101/focus-analysis/selection?startMillis=1778832000000&endMillis=1778832010000&maxSamples=64000
```

응답은 선택 구간의 `features`, `fft`, `anomalyScore`, `prediction`, `confidence`, `modelVersion` 등을 포함한다.

### 7.9 대시보드 API

#### `GET /api/dashboard/frontend`

메인 대시보드용 API. FE는 2초마다 갱신한다.

응답:

```json
{
  "factoryOee": 91.2,
  "statusDonut": {
    "running": 21,
    "standby": 1,
    "alarm": 2,
    "maintenance": 0,
    "total": 24
  },
  "alarmSummary": {
    "total": 2,
    "critical": 0,
    "warning": 2,
    "resolved": 0,
    "open": 2
  },
  "lineStats": [
    { "lineId": "LINE-01", "lineName": "Line A", "oee": 92.4 }
  ],
  "oeeHourlySeries": [
    {
      "lineId": "LINE-01",
      "lineName": "Line A",
      "data": [
        { "time": "00:00", "oee": 90.0 }
      ]
    }
  ]
}
```

OEE는 `GET /api/lines`의 라인별 `latestOee`를 기반으로 평균 산정한다. 알람 요약은 실시간 진동 window의 `alarmLevel`을 우선 반영하고, 실시간 알람이 없으면 열린 알람 테이블을 fallback으로 사용한다.

#### `GET /api/dashboard/summary`

운영/요약용 API.

응답:

| 필드 | 설명 |
| --- | --- |
| `equipmentCount` | 전체 설비 수 |
| `recentAnalysisCount` | 최근 분석 결과 수 |
| `recentAlarmCount` | 전체 알람 이력 수 |
| `equipmentStatusDistribution` | 설비 상태 분포 |
| `alarmLevelDistribution` | 알람 레벨 분포 |

### 7.10 운전 로그 API

#### `GET /api/operation-logs`

설비의 운전 상태 이력 조회.

```http
GET /api/operation-logs?equipmentCode=LINE-01_CAST-01&from=2026-05-15T00:00:00&to=2026-05-16T00:00:00
```

응답 item:

| 필드 | 설명 |
| --- | --- |
| `operationLogId` | 운전 로그 ID |
| `equipmentCode` | 설비 코드 |
| `statusCode` | 상태 코드 |
| `startAt` | 시작 시각 |
| `endAt` | 종료 시각 |
| `durationMin` | 지속 시간 분 |

#### `POST /api/operation-logs`

운전 로그 수동 저장.

```http
POST /api/operation-logs
Content-Type: application/json

{
  "equipmentCode": "LINE-01_CAST-01",
  "statusCode": "RUNNING",
  "startAt": "2026-05-15T12:00:00",
  "endAt": "2026-05-15T12:10:00"
}
```

### 7.11 인증/사용자 API

팝업 자체에는 필수는 아니지만, SWMP에서 사용자 권한 또는 1:1 채팅을 연결할 때 사용한다.

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 로그인 |
| `POST` | `/api/auth/signup` | 회원가입 |
| `POST` | `/api/auth/find-id` | 아이디 찾기 |
| `GET` | `/api/auth/security-question?loginId=...` | 보안 질문 조회 |
| `POST` | `/api/auth/reset-password` | 비밀번호 재설정 |
| `GET` | `/api/users` | 사용자 목록 |
| `GET` | `/api/users?roleName=MANAGER` | 역할별 사용자 목록 |
| `POST` | `/api/users` | 사용자 생성 |
| `PATCH` | `/api/users/{userId}/role` | 역할/라인 변경 |

사용자 응답:

```json
{
  "userId": "U101",
  "loginId": "line01_manager",
  "userName": "1라인 관리자",
  "email": "line01.manager@uecada.com",
  "roleName": "MANAGER",
  "lineId": "LINE-01",
  "createdAt": "2026-05-15T12:00:00"
}
```

### 7.12 커뮤니티 API

팝업 연동에는 직접 필요하지 않지만, SWMP가 커뮤니티/알림/자동문서화 영역까지 함께 가져갈 때 사용한다.

#### `GET /api/community/line-groups`

라인별 관리자/작업자 그룹.

응답 item:

```json
{
  "lineId": "LINE-01",
  "lineName": "LINE 1",
  "managers": [
    {
      "userId": "U101",
      "loginId": "line01_manager",
      "userName": "1라인 관리자",
      "roleName": "MANAGER",
      "lineId": "LINE-01"
    }
  ],
  "operators": []
}
```

#### `GET /api/posts`

공지사항/Q&A/자료실 글 목록.

Query:

| 이름 | 설명 |
| --- | --- |
| `category` | `NOTICE`, `QNA`, `RESOURCE` 등 |
| `targetLineId` | 특정 라인 글만 조회 |

#### `POST /api/posts`

글 작성.

```json
{
  "authorUserId": "U001",
  "title": "A라인 점검 안내",
  "content": "14:00부터 점검합니다.",
  "category": "NOTICE",
  "targetLineId": "LINE-01",
  "notice": true
}
```

#### `GET /api/community/chat/rooms`

현재 사용자가 접근 가능한 채팅방 목록.

```http
GET /api/community/chat/rooms?currentUserId=U001
```

응답 item:

| 필드 | 설명 |
| --- | --- |
| `chatRoomId` | 채팅방 ID |
| `lineId` | 라인 채팅방의 라인 |
| `roomName` | 방 이름 |
| `roomType` | `LINE` 또는 `DIRECT` |
| `userAId`, `userBId` | 1:1 채팅 참여자 |
| `createdAt` | 생성 시각 |
| `unreadCount` | 현재 사용자 기준 미확인 메시지 수 |

#### `POST /api/community/chat/rooms/direct`

1:1 채팅방 생성 또는 기존 방 반환.

```json
{
  "requesterUserId": "U001",
  "targetUserId": "U102"
}
```

권한:

- `ADMIN`은 모든 사용자와 1:1 채팅 가능.
- `MANAGER`, `OPERATOR`는 같은 라인 사용자와 1:1 채팅 가능.

#### `GET /api/community/chat/rooms/{roomId}/messages`

채팅 메시지 조회. 조회 시 해당 사용자의 읽음 상태가 갱신된다.

```http
GET /api/community/chat/rooms/1/messages?currentUserId=U001
```

#### `POST /api/community/chat/rooms/{roomId}/messages`

메시지 전송.

```json
{
  "senderUserId": "U001",
  "messageContent": "점검 결과 공유 부탁드립니다."
}
```

#### `GET /api/community/factory-report`

자동 문서화 markdown 생성.

Query:

| type | 문서 |
| --- | --- |
| `heat_safety` | 폭염 안전관리 보고서 |
| `annual_esg` | 연간 ESG 운영 보고서. 월별/일별 요약 포함 |
| `energy_emission` | 전력 사용 및 탄소 배출 보고서 |

응답:

```json
{
  "generatedAt": "2026-05-15T03:20:00Z",
  "reportType": "heat_safety",
  "title": "폭염 안전관리 보고서",
  "markdown": "# 폭염 안전관리 보고서\n\n| 항목 | 값 |\n| --- | --- |\n| ..."
}
```

## 8. 설비 상세보기 팝업 데이터 매핑

### 8.1 팝업 헤더

| 화면 영역 | 데이터 출처 | 필드/계산 |
| --- | --- | --- |
| 설비 이름 | `GET /api/equipments` | `equipmentName` |
| 라인 그룹 설비명 | `GET /api/equipments` | `location`, `processType` |
| 운전상태 | `GET /api/equipment-status` | `statusCode` |
| 가동률 | `POST /api/sensors/latest-values` | 최신 frame 시각 기반 freshness. 5초 이내 100%, 30초 이상 0% |
| 싸이클 타임 | `POST /api/sensors/latest-values` | `{prefix}:cycle_time` |

상태 라벨 권장 매핑:

| statusCode | 화면 라벨 |
| --- | --- |
| `RUNNING` | 운전 |
| `STANDBY` | 대기 |
| `ALARM` | 정지/알람 |
| `MAINTENANCE` | 점검 |

### 8.2 공통 상세내역

설비 코드가 `LINE-01_CAST-01`이면 prefix는 `LINE01.CAST01`이다.

| 화면 라벨 | bufferKey | format |
| --- | --- | --- |
| 전류 | `LINE01.CAST01:sensor_current` | `47.2A` |
| 전압 | `LINE01.CAST01:sensor_voltage` | `220.4V` |
| 온도 | `LINE01.CAST01:sensor_temperature` | `33.8℃` |
| 진동 | `LINE01.CAST01:sensor_vibration` | `1.918` |

공통 상세내역은 Type Data와 동일하게 2초 polling을 적용한다.

### 8.3 Type Data

설비의 `processType`에 따라 5.2 표의 3개 metric만 요청한다.

주조기 예시 요청:

```json
{
  "bufferKeys": [
    "LINE01.CAST01:injection_pressure",
    "LINE01.CAST01:mold_temperature",
    "LINE01.CAST01:cooling_flow"
  ]
}
```

검사기 `result_ok` 표시:

| 값 | 화면 표시 |
| --- | --- |
| `value >= 0.5` | `true` 또는 `OK` |
| `value < 0.5` | `false` 또는 `NG` |

### 8.4 원본 진동 데이터 window

우선순위:

1. `GET /api/vibration/realtime/{equipmentCode}`의 `values`.
2. `received=false`이면 `GET /api/equipments/{equipmentCode}/vibration-windows/raw-series`.

ECharts 데이터 변환:

```js
function rawSeriesFromRealtime(response) {
  const samplingRate = response.window?.samplingRate ?? 16000
  const baseTime = response.receivedAt ? new Date(response.receivedAt).getTime() : Date.now()
  const intervalMs = 1000 / samplingRate
  return response.values.map((value, index) => [baseTime + index * intervalMs, value])
}
```

### 8.5 FFT 차트

데이터 출처:

```text
response.analysis.fft.frequencies
response.analysis.fft.magnitudes
```

ECharts 데이터 변환:

```js
const fftData = response.analysis.fft.frequencies.map((freq, index) => [
  freq,
  response.analysis.fft.magnitudes[index] ?? 0
])
```

### 8.6 AI/feature 카드

| 화면 항목 | 데이터 출처 |
| --- | --- |
| AI 예측 후보 | `analysis.prediction` |
| 신뢰도 | `analysis.confidence` |
| RMS | `analysis.features.rms` |
| Peak-to-Peak | `analysis.features.peakToPeak` |
| Crest Factor | `analysis.features.crestFactor` |
| Kurtosis | `analysis.features.kurtosis` |
| 분석 샘플 | `analysis.modelInputSize` 또는 `window.valuesLength` |
| 원본 샘플 | `window.windowSize` |

### 8.7 구간 특징값 흐름

데이터 출처:

```http
GET /api/equipments/{equipmentCode}/analysis-results?limit=80
```

ECharts series:

| series | field |
| --- | --- |
| RMS | `rms` |
| Peak-to-Peak | `peakToPeak` |
| 이상 점수 | `anomalyScore` |

위험 marker:

- `alarmLevel === "warning"` 또는 `"danger"`인 point에 markPoint 표시.
- 최신 point가 `danger`이면 큰 알람 팝업을 띄울 수 있다.

## 9. 라인 상세보기 팝업 데이터 매핑

### 9.1 라인 헤더

| 화면 항목 | 데이터 출처 |
| --- | --- |
| 라인명 | `GET /api/lines`의 `lineName` |
| 전체 알람 현황 | `openAlarmCount`, `GET /api/alarms?status=OPEN` |
| 처리완료/미처리 | `GET /api/alarms`의 `status` 집계 |

### 9.2 라인별 설비 아이콘 레이아웃

1. `GET /api/equipments?factoryId=FACTORY-01` 조회.
2. `equipment.location === lineId`인 설비만 필터링.
3. `processType` 또는 설비 코드 prefix로 공정에 배치.

공정 순서:

| stage key | processType | 설비 코드 prefix | 화면 라벨 |
| --- | --- | --- | --- |
| `casting` | `주조` | `CAST` | 주조기 |
| `machining` | `가공` | `CNC` | 가공기 |
| `washing` | `세척` | `WASH` | 세척기 |
| `assembly` | `조립` | `ASSY` | 조립기 |
| `inspection` | `검사` | `TEST` | 검사기 |

설비 노드 데이터:

| 노드 항목 | 출처 |
| --- | --- |
| 설비명 | `equipmentName` |
| 설비 코드 | `equipmentCode` |
| 상태 색상 | `/api/equipment-status`의 `statusCode` |
| CT | `{prefix}:cycle_time` |
| 온도 | `{prefix}:sensor_temperature` |

### 9.3 종합 설비 효율 라인

우선순위:

1. `GET /api/lines`의 `latestOee`.
2. `latestOee`가 없으면 설비 상태 기반으로 파생 계산.

파생 계산:

```text
score = (running * 1.0 + standby * 0.35 + maintenance * 0.15) / total * 100
```

### 9.4 라인 설비 상태 분포도

데이터 출처:

```text
equipmentRunning
equipmentAlarm
equipmentStandby
equipmentMaintenance
equipmentTotal
```

도넛 차트 mapping:

| series name | value |
| --- | --- |
| 가동 | `equipmentRunning` |
| 정지/알람 | `equipmentAlarm` |
| 대기 | `equipmentStandby` |
| 점검 | `equipmentMaintenance` |

### 9.5 라인밸런싱

설비별 `cycle_time`을 공정별 평균 CT로 묶어 계산한다.

```text
공정 평균 CT = 해당 공정 설비 cycle_time 평균
라인밸런싱(%) = min(공정 평균 CT) / max(공정 평균 CT) * 100
공정 bar value = min(공정 평균 CT) / 해당 공정 평균 CT * 100
```

cycle_time이 없는 공정은 0 또는 `수신 대기`로 표시한다.

### 9.6 라인 생산성 분석

```text
병목 CT = max(공정 평균 CT)
UPH = 3600 / 병목 CT
UPMH = UPH * 가동 설비 수
생산성(%) = latestOee 기반 표시
```

추천 표시:

| 항목 | format |
| --- | --- |
| `UPH` | 정수 |
| `UPMH` | 정수 |
| 생산성 | `latestOee` 또는 계산 OEE `%` |

## 10. 에러/빈 데이터 처리 규칙

| 상황 | API 응답 | 화면 처리 |
| --- | --- | --- |
| 센서 버퍼 없음 | `latest=null`, `size=0` | `데이터 수신 대기` |
| 진동 window 없음 | `received=false`, `values=[]` | 원본 차트에 `진동 window 수신 대기` 표시 |
| DB raw window 없음 | `404` | 실시간 API만 사용하거나 빈 차트 표시 |
| 분석 결과 없음 | `[]` | 구간 특징값 흐름에 `분석 결과 대기` |
| 설비 metadata 없음 | `equipment=null` 또는 목록에 없음 | 팝업 열기 차단 또는 `설비를 찾을 수 없음` |
| 오래된 센서값 | `Date.now() - latest.timestampMs > 30000` | stale 스타일, 가동률 0% |
| 같은 알람 중복 | 같은 `alarmId` 또는 `equipmentCode + alarmType + status=OPEN` | 화면 팝업은 1개로 합쳐 표시 |

## 11. PowerShell 점검 예시

PowerShell에서 JSON이 잘리면 `ConvertTo-Json -Depth 10`을 붙인다.

```powershell
# 백엔드 상태
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8080/api/database/status

# 파이프라인 상태
Invoke-RestMethod http://localhost:8080/api/pipeline/status | ConvertTo-Json -Depth 10

# 설비/라인 목록
Invoke-RestMethod "http://localhost:8080/api/equipments?factoryId=FACTORY-01" | ConvertTo-Json -Depth 6
Invoke-RestMethod "http://localhost:8080/api/lines?factoryId=FACTORY-01" | ConvertTo-Json -Depth 6

# 설비 상태
Invoke-RestMethod "http://localhost:8080/api/equipment-status?equipIds=LINE-01_CAST-01,LINE-01_CNC-01" | ConvertTo-Json -Depth 6

# 센서 최신값
$body = @{
  bufferKeys = @(
    "LINE01.CAST01:sensor_current",
    "LINE01.CAST01:sensor_voltage",
    "LINE01.CAST01:sensor_temperature",
    "LINE01.CAST01:sensor_vibration",
    "LINE01.CAST01:cycle_time"
  )
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/sensors/latest-values" -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 6

# 실시간 진동
Invoke-RestMethod "http://localhost:8080/api/vibration/realtime/LINE-01_CAST-01" | ConvertTo-Json -Depth 10

# 분석 이력
Invoke-RestMethod "http://localhost:8080/api/equipments/LINE-01_CAST-01/analysis-results?limit=10" | ConvertTo-Json -Depth 10

# 알람
Invoke-RestMethod "http://localhost:8080/api/alarms?status=OPEN" | ConvertTo-Json -Depth 6

# SWMP helper
Invoke-RestMethod "http://localhost:8080/api/swmp/equipment/LINE-01_CAST-01" | ConvertTo-Json -Depth 10
Invoke-RestMethod "http://localhost:8080/api/swmp/lines/LINE-01" | ConvertTo-Json -Depth 10
```

## 12. SWMP 구현용 TypeScript 인터페이스

SWMP에서 직접 타입을 만들 때 아래 인터페이스를 기준으로 잡으면 된다.

```ts
export interface Equipment {
  id: number
  equipmentCode: string
  equipmentName: string
  processType: '주조' | '가공' | '세척' | '조립' | '검사' | string
  model: string | null
  installDate: string | null
  location: string | null
  locationX: number | null
  locationY: number | null
  createdAt: string | null
}

export interface EquipmentStatusItem {
  equipId: string
  statusCode: 'RUNNING' | 'STANDBY' | 'ALARM' | 'MAINTENANCE' | string
  updatedAt: string | null
}

export interface LineSummary {
  lineId: string
  lineName: string
  lineStatus: string
  factoryId: string
  equipmentTotal: number
  equipmentRunning: number
  equipmentAlarm: number
  equipmentStandby: number
  equipmentMaintenance: number
  openAlarmCount: number
  latestOee: number | null
}

export interface SensorFrame {
  timestampMs: number
  value: number
}

export interface SensorBufferLatest {
  bufferKey: string
  size: number
  capacity: number
  latest: SensorFrame | null
}

export interface VibrationRealtimeResponse {
  received: boolean
  equipmentId: string
  receivedAt: string | null
  window: {
    equipmentId: string
    timestamp: string | null
    samplingRate: number | null
    rpm: number | null
    windowSize: number | null
    windowIndex: number | null
    valuesLength: number
  } | null
  values: number[]
  analysis: {
    analysisResultId?: number | null
    vibrationWindowId?: number | null
    rawWindowSaved?: boolean | null
    alarmCreated?: boolean | null
    equipmentId?: string | null
    timestamp?: string | null
    samplingRate?: number | null
    rpm?: number | null
    windowSize?: number | null
    windowIndex?: number | null
    features?: {
      rms?: number | null
      peakFrequency?: number | null
      peakToPeak?: number | null
      crestFactor?: number | null
      kurtosis?: number | null
    } | null
    fft?: {
      frequencyResolution?: number | null
      binCount?: number | null
      frequencies?: number[] | null
      magnitudes?: number[] | null
    } | null
    anomalyScore?: number | null
    alarmLevel?: string | null
    prediction?: string | null
    confidence?: number | null
    modelVersion?: string | null
    modelInputType?: string | null
    modelInputSize?: number | null
    modelExpectedInputSize?: number | null
    modelInputStrategy?: string | null
    modelStatus?: string | null
  } | null
}
```

## 13. SWMP 연동 체크리스트

팝업 연동 전 아래 순서로 확인한다.

1. `GET /health`가 `{ "status": "ok" }`를 반환한다.
2. `GET /api/pipeline/status`의 `sensorBufferKeys`가 비어 있지 않다.
3. `GET /api/vibration/realtime/LINE-01_CAST-01`의 `received`가 `true`다.
4. `GET /api/equipments?factoryId=FACTORY-01`에서 SWMP가 열려는 `equipmentCode`가 존재한다.
5. `GET /api/lines?factoryId=FACTORY-01`에서 SWMP가 열려는 `lineId`가 존재한다.
6. iframe 팝업이 로그인 화면으로 이동하면 UECADA 로그인 세션을 먼저 만든다.
7. SWMP가 API를 직접 호출하는 경우 브라우저 console에서 CORS 오류가 없는지 확인한다.

## 14. 화면별 최소 API 묶음

### 설비 상세보기 팝업 최소 묶음

```text
GET  /api/equipments?factoryId=FACTORY-01
GET  /api/equipment-status?equipIds={equipmentCode}
POST /api/sensors/latest-values
GET  /api/vibration/realtime/{equipmentCode}
GET  /api/equipments/{equipmentCode}/analysis-results?limit=80
GET  /api/alarms?equipmentCode={equipmentCode}&status=OPEN
```

### 라인 상세보기 팝업 최소 묶음

```text
GET  /api/lines?factoryId=FACTORY-01
GET  /api/equipments?factoryId=FACTORY-01
GET  /api/equipment-status?equipIds={lineEquipmentCodes}
POST /api/sensors/latest-values
GET  /api/alarms?status=OPEN
```

### 메인 대시보드 최소 묶음

```text
GET /api/dashboard/frontend
GET /api/alarms?status=OPEN
```

### 커뮤니티/알림 최소 묶음

```text
GET  /api/posts
GET  /api/community/line-groups
GET  /api/community/chat/rooms?currentUserId={userId}
GET  /api/community/chat/rooms/{roomId}/messages?currentUserId={userId}
POST /api/community/chat/rooms/{roomId}/messages
GET  /api/community/factory-report?type=heat_safety
```
