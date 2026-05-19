# UECADA 전체 시스템 실행/정합성 문서

이 문서는 실제 수정 대상 프로젝트 기준이다. `UECADA-feat-FE_BE_fin_0518` 폴더는 참고용이며, 사용자가 별도로 요청할 때만 참고한다.

## 0. 핵심 명령어 치트시트

아래 명령어는 모두 프로젝트 루트에서 실행한다.

```powershell
cd C:\Users\hwapyeong\Desktop\github\UECADA
```

### 처음 켤 때

1. DAS, equip-sim, X_DAS 실행

```powershell
.\total_das\start-all.cmd
```

`.ps1`을 직접 실행하고 싶으면 현재 PowerShell 창에서만 실행 정책을 우회한 뒤 실행한다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\total_das\start-all.ps1
```

2. DB, FastAPI, Spring Boot 실행

```powershell
docker compose up -d --build mysql ai-api backend
```

3. Frontend 실행

```powershell
cd .\frontend\UECADA_3
npm install
npm run dev
```

### 재시작할 때

DAS, equip-sim, X_DAS만 다시 올릴 때:

```powershell
.\total_das\start-all.cmd
```

Backend/FastAPI/DB만 다시 빌드해서 올릴 때:

```powershell
docker compose up -d --build mysql ai-api backend
```

Frontend만 다시 켤 때:

```powershell
cd .\frontend\UECADA_3
npm run dev
```

### 끌 때

Frontend는 `npm run dev`를 실행한 터미널에서 `Ctrl + C`로 끈다.

Backend/FastAPI/DB 끄기:

```powershell
docker compose down
```

DAS, equip-sim, X_DAS 끄기:

```powershell
.\total_das\stop-all.cmd
```

`.ps1`을 직접 실행하고 싶으면 현재 PowerShell 창에서만 실행 정책을 우회한 뒤 실행한다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\total_das\stop-all.ps1
```

전체를 한 번에 정리하고 싶으면 루트의 `docker compose down`과 `.\total_das\stop-all.cmd`를 모두 실행한다. MySQL 데이터까지 지우는 `docker compose down -v`는 DB 초기화가 필요할 때만 사용한다.

### 상태 확인

컨테이너 목록:

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

주요 health check:

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod http://localhost:8001/health
Invoke-RestMethod http://localhost:8080/api/pipeline/status
```

로그 확인:

```powershell
docker logs -f smart-factory-backend
docker logs -f smart-factory-ai-api
docker logs -f uecada_mysql
```

DAS 쪽 로그:

```powershell
Push-Location .\total_das\DAS
docker compose logs -f
Pop-Location

Push-Location .\total_das\X_DAS
docker compose logs -f
Pop-Location

Push-Location .\total_das\equip-sim
.\scripts\up-all.ps1 logs LINE-01
Pop-Location
```

### 이런 상황이면 이 명령어

PowerShell에서 `스크립트를 실행할 수 없습니다`, `PSSecurityException`, `UnauthorizedAccess`가 나오면:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\total_das\start-all.ps1
```

또는 `.cmd` 래퍼를 사용한다.

```powershell
.\total_das\start-all.cmd
.\total_das\stop-all.cmd
```

Docker 네트워크 라벨 오류가 나오면:

```powershell
docker ps -a --filter network=das_das-internal --format "table {{.Names}}\t{{.Status}}"
docker rm -f das-mosquitto das-node-red das-simulator
docker network rm das_das-internal
.\total_das\start-all.cmd
```

`port is already allocated`가 나오면 어떤 프로세스가 포트를 쓰는지 확인한다.

```powershell
Get-NetTCPConnection -LocalPort 5173,8080,8001,1883,1888,1890 -ErrorAction SilentlyContinue |
  Select-Object LocalPort, OwningProcess
```

프론트 개발 서버 5173 포트만 강제로 끄고 싶으면:

```powershell
$pid5173 = (Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue).OwningProcess
if ($pid5173) { Stop-Process -Id $pid5173 -Force }
```

Docker Desktop이 이상하거나 네트워크가 꼬였는지 보고 싶으면:

```powershell
docker network ls
docker compose ps
```

DB만 접속해서 확인하고 싶으면:

```powershell
docker exec -it uecada_mysql mysql -uuecada_user -puecada1234 uecada
```

주의: 아래 명령은 MySQL volume까지 삭제한다. DB를 완전히 초기화할 때만 사용한다.

```powershell
docker compose down -v
docker compose up -d --build mysql ai-api backend
```

## 1. 전체 구조

데이터 흐름은 다음 순서로 동작한다.

1. `total_das/DAS`
   - 공통 센서성 데이터와 진동 raw window를 생성한다.
   - 진동 window는 MQTT 토픽 `das/common/{line}/{equipment}/vibration/window`로 발행된다.

2. `total_das/equip-sim`
   - 라인별 PLC 시뮬레이터 27대를 컨테이너로 실행한다.
   - LINE-01/02/03 Node-RED 라인 DAS는 항상 아래 flow 파일을 빌드에 사용한다.
   - `total_das/equip-sim/nodered/flows_das_LINE-01.json`
   - `total_das/equip-sim/nodered/flows_das_LINE-02.json`
   - `total_das/equip-sim/nodered/flows_das_LINE-03.json`

3. `total_das/X_DAS`
   - 각 라인 DAS의 OPC UA 데이터를 통합한다.
   - Backend는 X_DAS OPC UA endpoint를 구독해서 Spring 메모리 버퍼에 넣는다.

4. `backend` Spring Boot
   - X_DAS OPC UA 값을 `SensorBufferRegistry`에 저장한다.
   - 진동 window MQTT를 수신한다.
   - window를 FastAPI `/analyze`로 전달한다.
   - Frontend와 SWMP가 읽기 쉬운 API 통로 역할을 한다.

5. `ai-api` FastAPI
   - feature, FFT, AI 모델 예측을 수행한다.
   - raw window는 10분마다 저장한다.
   - warning/danger 알람 발생 시 raw window를 즉시 저장한다.
   - raw window 저장 시 최신 온도/전류/전압/진동 센서 스냅샷도 함께 저장한다.

6. `mysql`
   - 설비/라인/사용자/게시판/채팅/알람/진동 window/분석 결과를 저장한다.

7. `frontend/UECADA_3`
   - 실시간 화면은 DB가 아니라 Spring Boot의 버퍼 API를 조회한다.
   - DB는 10분 raw 저장, 알람 저장, 과거 이력 조회에 사용한다.

## 2. 실행 순서

PowerShell을 프로젝트 루트에서 실행한다.

```powershell
cd C:\Users\hwapyeong\Desktop\github\UECADA
```

스크립트 실행 정책 때문에 막히면 현재 PowerShell 세션에서만 우회한다.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
```

DAS, equip-sim, X_DAS를 먼저 실행한다.

```powershell
.\total_das\start-all.cmd
```

`.ps1`을 직접 실행하는 경우에는 실행 정책 우회 후 아래처럼 실행한다.

```powershell
.\total_das\start-all.ps1
```

스크립트는 어느 폴더에서 실행해도 자신의 위치를 기준으로 `DAS`, `equip-sim`, `X_DAS`를 찾는다.

### DAS Compose 네트워크 라벨 오류 해결

다른 PC에서 처음 실행하거나 예전 Docker Compose 실행 흔적이 남아 있으면 아래 오류가 날 수 있다.

```text
network das_das-internal was found but has incorrect label com.docker.compose.network set to "" (expected: "das-internal")
```

이 오류는 `total_das/DAS/docker-compose.yml`이 내부 네트워크 `das-internal`을 만들려고 하는데, Docker 안에 이미 같은 실제 이름의 네트워크 `das_das-internal`이 남아 있고 그 네트워크가 현재 Compose 프로젝트의 라벨을 갖고 있지 않을 때 발생한다. 보통 수동으로 네트워크를 만들었거나, 이전 프로젝트/이전 Compose 버전이 같은 이름의 네트워크를 남긴 경우다.

최신 `total_das/start-all.ps1`은 비어 있는 오래된 `das_das-internal` 네트워크를 자동으로 삭제한다. 그래도 컨테이너가 붙어 있어서 실패하면 아래 순서로 한 번만 정리한다.

```powershell
docker ps -a --filter network=das_das-internal --format "table {{.Names}}\t{{.Status}}"
docker rm -f das-mosquitto das-node-red das-simulator
docker network rm das_das-internal
.\total_das\start-all.ps1
```

Windows PowerShell 실행 정책 때문에 `PSSecurityException` 또는
`UnauthorizedAccess`가 나오면 `.cmd` 래퍼로 실행한다. 이 방법은 해당 실행
프로세스에만 `ExecutionPolicy Bypass`를 적용한다.

```powershell
.\total_das\start-all.cmd
```

만약 첫 번째 명령에서 UECADA가 아닌 다른 컨테이너가 보이면 그 컨테이너는 해당 프로젝트에서 먼저 내린 뒤 네트워크를 삭제한다. 이 정리는 `total-das-net`, `factory-net`, MySQL volume은 지우지 않으므로 DB 데이터에는 영향을 주지 않는다.

다음으로 DB, FastAPI, Spring Boot를 실행한다.

```powershell
docker compose up -d --build mysql ai-api backend
```

주의: 루트 `mosquitto` 서비스까지 같이 올리면 `total_das/DAS`의 `das-mosquitto`와 1883 포트가 충돌할 수 있다. 전체 시스템 검증 시에는 보통 `mysql ai-api backend`만 올린다.

Frontend를 실행한다.

```powershell
cd .\frontend\UECADA_3
npm install
npm run dev
```

접속 URL:

- Frontend: `http://127.0.0.1:5173`
- Spring Boot: `http://localhost:8080`
- FastAPI: `http://localhost:8001`
- DAS UI: `http://localhost:1888`
- LINE-01 UI: `http://localhost:2880`
- LINE-02 UI: `http://localhost:3880`
- LINE-03 UI: `http://localhost:4880`
- X_DAS UI: `http://localhost:1890`

## 3. 정합성 확인 API

Backend 상태:

```powershell
Invoke-RestMethod http://localhost:8080/health
```

FastAPI 상태:

```powershell
Invoke-RestMethod http://localhost:8001/health
```

Spring 버퍼와 진동 window 수신 상태:

```powershell
Invoke-RestMethod http://localhost:8080/api/pipeline/status
```

특정 설비의 실시간 진동 window:

```powershell
Invoke-RestMethod http://localhost:8080/api/vibration/realtime/LINE-01_CAST-01
```

특정 센서 버퍼 최신값:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/sensors/latest-values `
  -ContentType "application/json" `
  -Body '{"bufferKeys":["LINE01.CAST01:sensor_temperature","LINE01.CAST01:sensor_current","LINE01.CAST01:sensor_voltage","LINE01.CAST01:cycle_time"]}'
```

라인 요약:

```powershell
Invoke-RestMethod "http://localhost:8080/api/lines?factoryId=FACTORY-01"
```

## 4. 실시간 버퍼와 DB 저장 정책

실시간 화면은 Spring Boot의 `SensorBufferRegistry`와 `VibrationWindowMonitorService`를 기준으로 표시한다.

- X_DAS OPC UA 구독 주기: 기본 1초
- DAS/equip-sim 데이터 생성: 약 2초 주기
- Frontend polling: 알람 1초, 설비/라인 실시간 버퍼 2초
- 실시간 상세 진동: `/api/vibration/realtime/{equipmentCode}`
- 실시간 온도/전류/전압/싸이클타임: `/api/sensors/latest-values`

설비 상세 팝업의 `Type Data`는 진동 분석 결과를 섞지 않고 PLC 타입별 3개 공정값만 표시한다.

| 설비 타입 | 표시 버퍼 suffix |
| --- | --- |
| 주조기 | `injection_pressure`, `mold_temperature`, `cooling_flow` |
| 가공기 | `spindle_speed`, `tool_usage`, `coolant_flow` |
| 세척기 | `cleaning_concentration`, `cleaning_temperature`, `cleaning_pressure` |
| 조립기 | `tightening_torque`, `tightening_angle`, `press_force` |
| 검사기 | `bore_dimension`, `hole_dimension`, `result_ok` |

공통 상세내역은 DAS/X_DAS 공통 센서 버퍼의 `sensor_current`, `sensor_voltage`, `sensor_temperature`, `sensor_vibration`을 2초 polling으로 표시한다.

DB 저장은 FastAPI가 담당한다.

- 10분 주기 저장: `RAW_WINDOW_SAVE_INTERVAL_MINUTES=10`
- 알람 저장: AI 분석 결과 `alarmLevel`이 `warning` 또는 `danger`이면 즉시 저장
- 저장 테이블:
  - `vibration_window`: raw vibration values, sampling rate, rpm, window index, sensor snapshot
  - `analysis_result`: RMS, peak frequency, peak-to-peak, crest factor, kurtosis, prediction, confidence, anomaly score, alarm level
  - `alarm_history`: AI 분석 기반 알람 열림/닫힘 이력

`vibration_window`에는 다음 스냅샷 컬럼이 추가되어 window 저장 시점의 주변 센서를 같이 남긴다.

- `sensor_temperature`
- `sensor_current`
- `sensor_voltage`
- `sensor_vibration`
- `sensor_snapshot_json`

FastAPI는 오래된 Docker volume에도 맞도록 시작 시 누락 컬럼을 자동 추가한다.

## 5. OEE 산정 방식

현재 라인 OEE는 실시간 상태를 반영하는 파생 지표다.

Spring Boot `LineAggregationService` 기준:

```text
OEE = ((RUNNING * 1.0) + (STANDBY * 0.35) + (MAINTENANCE * 0.15)) / TOTAL * 100
```

알람 판단 우선순위:

1. 실시간 AI 분석 결과가 `warning` 또는 `danger`이면 설비 상태는 `ALARM`
2. 실시간 AI 분석 결과가 `normal`이면 기존 DB `ALARM` 상태를 `RUNNING`으로 보정
3. X_DAS 센서 버퍼의 최신 수신 시각이 30초 이내면 `RUNNING`
4. 120초 이내면 `STANDBY`
5. 그 이상이면 `MAINTENANCE`
6. 위 조건이 없으면 DB의 `equipment_status`를 사용

따라서 OEE는 DB 고정값이 아니라 X_DAS 버퍼와 AI 분석 상태에 따라 실시간으로 변한다.

## 6. SWMP 연동

SWMP에서 우리 Vue 화면을 팝업으로 열 때 사용할 URL:

- 설비 상세 팝업: `http://127.0.0.1:5173/#/equipment?equipmentId=LINE-01_CAST-01&popup=1`
- 라인 상세 팝업: `http://127.0.0.1:5173/#/layout?lineId=LINE-01&popup=1`
- 팝업 화면 전용 API 명세: [`popup-api-spec.md`](popup-api-spec.md)

SWMP가 직접 데이터만 가져갈 때 사용할 API:

- `GET /api/swmp/equipment/{equipmentCode}`
  - 설비 기본 정보
  - 최신 센서 버퍼값
  - 최신 진동 window와 AI 분석값
  - Vue 팝업 URL

- `GET /api/swmp/lines/{lineId}`
  - 라인 OEE
  - 설비 상태 분포
  - Vue 라인별 현황 화면의 라인 상세 팝업 URL

예시:

```powershell
Invoke-RestMethod http://localhost:8080/api/swmp/equipment/LINE-01_CAST-01
Invoke-RestMethod http://localhost:8080/api/swmp/lines/LINE-01
```

## 7. 로그인 기본 계정

초기 비밀번호와 보안 답변은 모두 `secret`이다.

| 역할 | 라인 | 로그인 ID |
| --- | --- | --- |
| 전체 관리자 | 전체 | `admin` |
| LINE-01 관리자 | LINE-01 | `line01_manager` |
| LINE-01 작업자 | LINE-01 | `line01_operator` |
| LINE-02 관리자 | LINE-02 | `line02_manager` |
| LINE-02 작업자 | LINE-02 | `line02_operator` |
| LINE-03 관리자 | LINE-03 | `line03_manager` |
| LINE-03 작업자 | LINE-03 | `line03_operator` |

로그인 기능:

- `POST /api/auth/login`
- `POST /api/auth/signup`
- `POST /api/auth/find-id`
- `GET /api/auth/security-question?loginId=...`
- `POST /api/auth/reset-password`

회원가입 시 사용자 ID, 로그인 ID, 라인, 역할, 비밀번호, 보안 질문/답변을 저장한다.

## 8. 커뮤니티 기능

Frontend 커뮤니티 화면은 다음 API를 사용한다.

- `GET /api/community/line-groups`
  - 라인별 관리자/작업자 그룹
- `GET /api/posts?targetLineId=LINE-01`
  - 라인별 공지/게시글
- `POST /api/posts`
  - 관리자/사용자가 게시글 작성
- `GET /api/community/chat/rooms?currentUserId=U001`
  - 사용자 권한에 맞는 채팅방 목록
- `GET /api/community/chat/rooms/{roomId}/messages?currentUserId=U001`
  - 채팅 메시지 조회
- `POST /api/community/chat/rooms/{roomId}/messages`
  - 메시지 전송
- `POST /api/community/chat/rooms/direct`
  - 1:1 채팅방 생성
- `GET /api/community/factory-report?type=heat_safety`
  - 폭염 안전관리 보고서 자동 문서화
- `GET /api/community/factory-report?type=annual_esg`
  - 연간 ESG 운영 보고서 자동 문서화. 현재 버퍼 기준 일별/월별/연간 환산 포함
- `GET /api/community/factory-report?type=energy_emission`
  - 전력 사용 및 탄소배출 보고서 자동 문서화

채팅 권한:

- `ADMIN`: 모든 라인 채팅과 1:1 채팅 가능
- `MANAGER`, `OPERATOR`: 자기 라인 채팅 가능
- 1:1 채팅은 관리자 전체 가능, 그 외 사용자는 같은 라인 사용자끼리 가능

자동 문서화 탭은 3개다.

- 폭염 안전관리 보고서: 온도 기준 관심/주의/경고/심각 설비 수, 조치 기준, 설비별 온도 현황
- 연간 ESG 운영 보고서: 설비 상태, OEE, 알람, 온도 안전, 전력/탄소 배출을 일별·월별·연간으로 환산
- 전력 사용 및 탄소배출 보고서: 전압·전류 기반 순간전력, 일/월/연 환산 전력사용량, 배출량, 주요 기여 설비

산정 기준:

```text
power_w = voltage_v * current_a
energy_kwh = Σ(power_w * interval_hour) / 1000
emissions_tco2eq = energy_kwh * emission_factor_tco2_per_kwh
```

현재 구현은 실시간 버퍼의 최신값을 기준으로 자동 보고서를 만든다. 장기 누적 DB가 충분히 쌓이면 같은 API 내부에서 실제 누적 전력량 기준으로 보정할 수 있다.

## 9. DB 점검 쿼리

```powershell
docker exec -it uecada_mysql mysql -uuecada_user -puecada1234 uecada
```

```sql
SELECT COUNT(*) FROM vibration_window;
SELECT equipment_code, measured_at, sensor_temperature, sensor_current, sensor_voltage
FROM vibration_window
ORDER BY id DESC
LIMIT 5;

SELECT equipment_code, prediction, confidence, anomaly_score, alarm_level, created_at
FROM analysis_result
ORDER BY id DESC
LIMIT 5;

SELECT status, COUNT(*)
FROM alarm_history
GROUP BY status;
```

## 10. 문제 발생 시 빠른 확인

자주 쓰는 실행/중지/재시작 명령은 문서 맨 위의 `0. 핵심 명령어 치트시트`를 먼저 본다. 아래는 작업 중 추가 확인이 필요할 때 쓰는 명령이다.

컨테이너 상태:

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

DAS/equip-sim/X_DAS 끄기:

```powershell
.\total_das\stop-all.cmd
```

DAS/X_DAS만 재시작:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
.\total_das\start-all.ps1
```

Backend/FastAPI/DB만 재시작:

```powershell
docker compose up -d --build mysql ai-api backend
```

Frontend 변경 반영:

```powershell
cd .\frontend\UECADA_3
npm run dev
```

Vite dev server가 이미 켜져 있으면 브라우저 강력 새로고침 또는 dev server 재시작을 수행한다.
