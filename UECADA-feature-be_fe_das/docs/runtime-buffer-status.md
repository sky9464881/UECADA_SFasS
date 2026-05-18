# Runtime Buffer Status

Snapshot: `2026-05-14 17:32:15 +09:00`

Backend는 X_DAS OPC UA 값을 `SensorBufferRegistry` 인메모리 ring buffer에 저장한다. 이 값은 DB에 저장하지 않고, `/api/sensors` API로 현재 buffer key와 최신 frame을 확인한다.

정식 명세는 `docs/das-backend-buffer-spec.md` 기준이다.

이 스냅샷은 backend 컨테이너 재기동 직후 다시 적재된 인메모리 버퍼 상태다.

## Buffer Snapshot

| 공정 | 설비 ID | 버퍼 키 | 저장 데이터 항목 | 단위 | 현재/용량 | 최신값 | 지연(s) | 상태 |
| --- | --- | --- | --- | --- | ---: | ---: | ---: | --- |
| 주조 | CAST-01 | `CAST01:temperature` | 용탕 온도 | C | 31/600 | 213.902 | 1.4 | OK |
| 주조 | CAST-01 | `CAST01:pressure` | 사출 압력 | bar | 31/600 | 82.013 | 1.4 | OK |
| 주조 | CAST-01 | `CAST01:cycle_time` | 사이클 타임 | s | 3/100 | 23.286 | 11.3 | OK |
| 가공 | CNC-01 | `CNC01:spindle_load` | 주축 부하율 | % | 31/7200 | 28.882 | 1.4 | OK |
| 가공 | CNC-01 | `CNC01:spindle_rpm` | 주축 회전수 | RPM | 30/7200 | 5486 | 1.4 | OK |
| 가공 | CNC-01 | `CNC01:feed_rate` | 이송 속도 | mm/s | 31/3600 | 18.475 | 1.4 | OK |
| 세척 | WASH-01 | `WASH01:water_temp` | 세척수 온도 | C | 31/300 | 62.1057 | 1.4 | OK |
| 세척 | WASH-01 | `WASH01:flow_rate` | 유량 | L/min | 31/300 | 52.3719 | 1.4 | OK |
| 조립 | ASSY-01 | `ASSY01:torque` | 체결 토크 | N*m | 32/500 | 39.6107 | 1.4 | OK |
| 검사 | TEST-01 | `TEST01:leak_pressure` | 리크 검사 압력 | Pa | 32/200 | 0.6299 | 1.4 | OK |
| 공통(DAS) | CAST-01 | `CAST01:sensor_vibration` | DAS 진동 RMS | mm/s | 5/600 | 2.0325 | 3.7 | OK |
| 공통(DAS) | CAST-01 | `CAST01:sensor_current` | DAS 전류 | A | 4/600 | 43.1034 | 8.1 | OK |
| 공통(DAS) | CAST-01 | `CAST01:sensor_voltage` | DAS 전압 | V | 4/600 | 386.0717 | 8.1 | OK |
| 공통(DAS) | CAST-01 | `CAST01:sensor_temperature` | DAS 설비 온도 | C | 4/600 | 69.8541 | 8.1 | OK |

Notes:

- `CAST01:*`, `CNC01:*` 같은 key는 LINE-01 호환 alias다. 실제 중복 방지용 line-scoped key도 같이 채워진다. 예: `LINE01.CAST01:temperature`.
- DAS/X_DAS 공통 센서값은 설비별 `sensor_vibration`, `sensor_current`, `sensor_voltage`, `sensor_temperature`로 들어온다.
- `ENV:*`, `vibration_x`, `vibration_y`, `vibration_z` key는 현재 명세에서 제외한다. DAS의 진동 대표값은 설비별 `sensor_vibration`이며, raw vibration window는 MQTT로 AI 분석 경로에 들어간다.
- Raw vibration window는 MQTT로 backend에 들어와 FastAPI `/analyze`로 전달된다. 이 raw window는 위 OPC UA scalar buffer와 별도 흐름이다.

## AI Pipeline Check

| 확인 항목 | 결과 |
| --- | --- |
| Backend vibration latest API | `received=true`, `receivedCount=52` |
| 최신 진동 입력 | `equipmentId=LINE-01_CAST-01`, `samplingRate=16000`, `windowSize=32000`, `windowIndex=6353`, `valuesLength=32000` |
| FastAPI 호출 | 최근 2분 로그에서 `POST /analyze HTTP/1.1 200 OK` 반복 확인 |
| FastAPI 모델 상태 | `modelStatus=loaded` |
| 모델 입력 전략 | `stft_spectrogram_64x64_from_raw` |
| 최신 DB 저장 결과 | `analysis_result.id=6752`, `equipment_code=LINE-01_CAST-01`, `prediction=bearing`, `confidence=0.8633`, `alarm_level=danger` |

Latest DB rows checked:

| id | equipment_code | rms | peak_frequency | prediction | confidence | model_input_strategy | model_status | alarm_level | created_at |
| ---: | --- | ---: | ---: | --- | ---: | --- | --- | --- | --- |
| 6752 | LINE-01_CAST-01 | 3.27849974 | 20 | bearing | 0.8633 | stft_spectrogram_64x64_from_raw | loaded | danger | 2026-05-14 17:32:25 |
| 6751 | LINE-01_CAST-01 | 3.36660015 | 20 | bearing | 0.8633 | stft_spectrogram_64x64_from_raw | loaded | danger | 2026-05-14 17:32:24 |
| 6750 | LINE-01_CAST-01 | 3.26959994 | 20 | bearing | 0.8633 | stft_spectrogram_64x64_from_raw | loaded | danger | 2026-05-14 17:32:23 |
| 6749 | LINE-01_CAST-01 | 2.15819971 | 20 | bearing | 0.85 | stft_spectrogram_64x64_from_raw | loaded | danger | 2026-05-14 17:32:22 |
| 6748 | LINE-01_CAST-01 | 2.06249968 | 20 | bearing | 0.85 | stft_spectrogram_64x64_from_raw | loaded | danger | 2026-05-14 17:32:21 |

## Check Commands

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/sensors | ConvertTo-Json -Depth 6
Invoke-RestMethod -Uri "http://localhost:8080/api/sensors/CAST01:temperature?last=1" | ConvertTo-Json -Depth 5
Invoke-RestMethod -Uri http://localhost:8080/api/vibration/latest | ConvertTo-Json -Depth 6
docker logs --since 2m smart-factory-ai-api
docker exec uecada_mysql mysql -uuecada_user -puecada1234 -Duecada -e "SELECT id, equipment_code, rms, peak_frequency, prediction, confidence, model_input_strategy, model_status, alarm_level, created_at FROM analysis_result ORDER BY id DESC LIMIT 5;"
```
