# Backend

Spring Boot 백엔드 위치입니다.

역할:

- MQTT vibration window 구독
- 원본 window 메타데이터 저장
- FastAPI 분석 요청
- 분석 결과와 알람 이력 저장
- Vue 대시보드용 REST API 제공

패키지는 기능 기준으로 나눕니다.

```text
common/      공통 응답, 예외, 유틸
config/      CORS, MQTT, WebClient, JPA 설정
equipment/   설비 정보
vibration/   원본 진동 window 수신/저장
analysis/    FFT/AI 분석 결과 저장
alarm/       알람 이력
dashboard/   화면 조회용 통합 API
```

설정 파일:

```text
application-local.yml   로컬 직접 실행용 localhost 설정
application-docker.yml  Docker Compose service name 기준 설정
```

Docker Compose 내부에서는 `localhost` 대신 `mysql`, `mosquitto`, `ai-api` 같은 service name을 사용합니다.

## Current Phase: MQTT Ingestion

현재 backend는 MQTT payload를 받아 원본 window 파일 저장, FastAPI 분석 호출, MySQL 저장까지 수행합니다.

구독 topic:

```text
factory/motor/1/vibration/window
```

기대 로그:

```text
Received MQTT message: equipmentId=MOTOR_001, windowIndex=0, samplingRate=16000, rpm=1200, windowSize=32000, valuesLength=32000
```

서버에서도 마지막 수신 메시지를 확인할 수 있습니다.

```bash
curl http://localhost:8080/api/vibration/latest
```

아직 받은 메시지가 없으면 `received=false`가 내려오고, MQTT 메시지를 받은 뒤에는 마지막 window 요약이 내려옵니다.

MQTT 메시지를 받은 뒤에는 FastAPI `/analyze`도 호출합니다. FastAPI가 켜져 있으면 아래 로그가 추가로 출력됩니다.

```text
FastAPI response: equipmentId=MOTOR_001, windowIndex=0, rms=0.12242235, peakFrequency=19.53125, peakToPeak=0.77802311, crestFactor=3.31153025, kurtosis=3.20201772, prediction=bearing, confidence=0.87, modelVersion=spectrogram-pca-rf-v1, modelInputStrategy=stft_spectrogram_64x64_from_raw, modelStatus=loaded, anomalyScore=0.389, alarmLevel=normal
```

## Run With Local Java

Ubuntu에 Java 21과 Gradle wrapper가 준비된 경우:

```bash
cd backend
./gradlew bootRun
```

Node-RED와 Mosquitto를 Ubuntu에서 직접 실행 중이면 이 방식이 가장 단순합니다. 현재 Spring Boot는 `application-local.yml` 기준으로 `localhost:1883`을 구독합니다.
FastAPI는 `localhost:8001`에서 먼저 실행되어 있어야 분석 로그까지 확인할 수 있습니다.

MySQL 연결도 활성화되어 있습니다. 로컬 실행 전 MySQL에 `database/init/01_schema.sql`, `database/init/02_seed.sql`을 적용해야 합니다.

DB 연결 확인:

```bash
curl http://localhost:8080/api/database/status
```

정상 예시:

```json
{"connected":true,"database":"smart_factory","equipmentCount":1}
```

프론트 성능을 위해 raw 시계열 조회 API는 원본 32000 sample window를 그대로 모두 보내지 않고 downsampling된 points를 반환합니다.

```bash
curl "http://localhost:8080/api/equipments/MOTOR_001/vibration-windows/raw-series?limit=5&maxPoints=8000"
```

`sampleCount`는 화면 표시용 points 수이고, `originalSampleCount`는 downsampling 전 원본 sample 수입니다.

로컬 디버깅 중 replay 데이터를 초기화하려면 Spring Boot를 `local` profile로 실행한 상태에서 아래 API를 호출합니다.

```bash
curl -X POST http://localhost:8080/api/debug/reset-data
```

삭제 대상은 `alarm_history`, `analysis_result`, `vibration_window`, `data/raw_windows/*`입니다. `equipment`은 유지됩니다. Node-RED flow의 `RESET DB + raw files` 버튼도 같은 API를 호출합니다.

## Run With Docker Compose

Docker Compose로 backend와 Mosquitto를 함께 띄울 수도 있습니다.

```bash
cd /home/hwapyeong/smart_factory_vib_monitoring
docker compose up --build backend
```

이미 Ubuntu에서 Mosquitto를 직접 실행 중이면 Docker Compose의 Mosquitto와 `1883` 포트가 충돌할 수 있습니다. 그 경우에는 로컬 Java 실행을 사용하거나, 먼저 로컬 Mosquitto를 중지한 뒤 Compose를 실행합니다.
