# DAS Backend Buffer Spec

이 문서는 현재 `total_das`에서 실제로 들어오는 값 기준의 backend 인메모리 버퍼 명세다. `ENV:*`, `vibration_x`, `vibration_y`, `vibration_z`는 사용하지 않는다.

## Data Flow

```text
equip-sim PLC data -> LINE Node-RED OPC UA -> X_DAS
DAS sensor data    -> DAS Node-RED MQTT/OPC UA -> X_DAS
X_DAS              -> Backend OPC UA subscription -> SensorBufferRegistry

DAS vibration raw window -> MQTT -> Backend -> FastAPI /analyze -> MySQL analysis_result
```

Backend의 `SensorBufferRegistry`는 `ConcurrentHashMap<String, SensorBuffer>` 구조이며, 각 `SensorBuffer`는 고정 용량 ring buffer로 동작한다. 오래된 값은 자동으로 삭제되고 DB에는 저장하지 않는다.

## Key Rule

| 구분 | 형식 | 예시 |
| --- | --- | --- |
| 라인 구분 key | `{LINE}.{EQUIP}:{metric}` | `LINE01.CAST01:sensor_vibration` |
| LINE-01 호환 alias | `{EQUIP}:{metric}` | `CAST01:sensor_vibration` |
| X_DAS OPC UA Node ID | `ns=2;s={LINE}.{EQUIP}.{Field}` | `ns=2;s=LINE01.CAST01.SensorVibration` |

`LINE01`, `LINE02`, `LINE03`는 충돌 방지를 위한 정식 key다. `CAST01:*` 같은 라인 없는 key는 기존 API/표 호환을 위해 LINE-01 값만 같이 적재한다.

## DAS Sensor Buffers

아래 4개 DAS 센서 버퍼는 모든 라인, 모든 설비에 동일하게 붙는다.

대상 설비:

```text
CAST01
CNC01, CNC02, CNC03
WASH01
ASSY01, ASSY02
TEST01, TEST02
```

| 공정 | 설비 ID | 버퍼 키 패턴 | 저장 데이터 항목 | DAS 원천 값 | 단위 | 버퍼 크기 | 만료/교체 정책 | FastAPI 전달 |
| --- | --- | --- | --- | --- | --- | ---: | --- | --- |
| 공통(DAS) | 모든 설비 | `{LINE}.{EQUIP}:sensor_vibration` | 설비별 진동 RMS | `vibration_rms` | mm/s | 600 | Oldest 자동 삭제 | raw vibration window가 MQTT로 별도 전달 |
| 공통(DAS) | 모든 설비 | `{LINE}.{EQUIP}:sensor_current` | 설비별 전류 | `current_a` | A | 600 | Oldest 자동 삭제 | 필요 시 분석 요청 payload에 포함 |
| 공통(DAS) | 모든 설비 | `{LINE}.{EQUIP}:sensor_voltage` | 설비별 전압 | `voltage_v` | V | 600 | Oldest 자동 삭제 | 필요 시 분석 요청 payload에 포함 |
| 공통(DAS) | 모든 설비 | `{LINE}.{EQUIP}:sensor_temperature` | 설비별 온도 | `equipment_temperature_c` | C | 600 | Oldest 자동 삭제 | 온도 이상/주기 분석 시 사용 |

예시:

```text
LINE01.CAST01:sensor_vibration
LINE02.CNC03:sensor_current
LINE03.TEST02:sensor_temperature
```

## PLC/Line Buffers

아래 값은 설비 PLC/라인 DAS 값이 X_DAS에서 BE용 OPC UA Node ID로 변환된 것이다.

| 공정 | 설비 ID | 버퍼 키 패턴 | 저장 데이터 항목 | 단위 | 버퍼 크기 | 만료/교체 정책 | FastAPI 전달 트리거 |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| 주조 | CAST-01 | `{LINE}.CAST01:temperature` | 용탕 온도 | C | 600 | Oldest 자동 삭제 | 온도 급변 또는 주기 분석 |
| 주조 | CAST-01 | `{LINE}.CAST01:pressure` | 사출 압력 | bar | 600 | Oldest 자동 삭제 | 압력 이상치 감지 |
| 주조 | CAST-01 | `{LINE}.CAST01:cycle_time` | 사이클 타임 | s | 100 | Oldest 자동 삭제 | OEE 계산 주기 |
| 가공 | CNC-01/02/03 | `{LINE}.CNC01:spindle_load` | 주축 부하율 | % | 7200 | Oldest 자동 삭제 | 부하 지속 이상 또는 주기 분석 |
| 가공 | CNC-01/02/03 | `{LINE}.CNC01:spindle_rpm` | 주축 회전수 | RPM | 7200 | Oldest 자동 삭제 | spindle_load와 동기 분석 |
| 가공 | CNC-01/02/03 | `{LINE}.CNC01:feed_rate` | 이송 속도 | mm/s | 3600 | Oldest 자동 삭제 | 주기 분석 |
| 세척 | WASH-01 | `{LINE}.WASH01:water_temp` | 세척수 온도 | C | 300 | Oldest 자동 삭제 | 온도 이탈 |
| 세척 | WASH-01 | `{LINE}.WASH01:flow_rate` | 유량 | L/min | 300 | Oldest 자동 삭제 | 유량 이상 |
| 조립 | ASSY-01/02 | `{LINE}.ASSY01:torque` | 체결 토크 | N*m | 500 | Oldest 자동 삭제 | 토크 분포 이상 |
| 검사 | TEST-01/02 | `{LINE}.TEST01:leak_pressure` | 리크 검사 압력 | Pa | 200 | Oldest 자동 삭제 | Fail 연속 발생 |

`CNC02`, `CNC03`, `ASSY02`, `TEST02`는 설비 번호만 바꾸어 같은 metric 규칙을 쓴다.

## AI Vibration Input

AI 모델의 진동 입력은 `sensor_vibration` scalar가 아니라 DAS의 raw vibration window다.

| 항목 | 값 |
| --- | --- |
| MQTT topic | `das/common/{line}/{equipment}/vibration/window` |
| Backend DTO equipmentId | `{line}_{equipment}` 예: `LINE-01_CAST-01` |
| raw input key | `values.vibration_raw` |
| samplingRate | `16000` |
| windowSize | `32000` |
| stride | `16000` |
| FastAPI endpoint | `POST /analyze` |
| DB table | `analysis_result` |

Backend는 이 window를 받으면 FastAPI로 전달하고, FastAPI가 `rms`, `peak_frequency`, `prediction`, `confidence`, `model_input_strategy`, `model_status`, `alarm_level` 등을 산출해 DB에 저장한다.

## Runtime Check

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/sensors | ConvertTo-Json -Depth 6
Invoke-RestMethod -Uri "http://localhost:8080/api/sensors/LINE01.CAST01:sensor_vibration?last=5" | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri http://localhost:8080/api/vibration/latest | ConvertTo-Json -Depth 6
docker logs --since 2m smart-factory-ai-api
docker exec uecada_mysql mysql -uuecada_user -puecada1234 -Duecada -e "SELECT id, equipment_code, rms, prediction, confidence, model_input_strategy, model_status, alarm_level, created_at FROM analysis_result ORDER BY id DESC LIMIT 5;"
```
