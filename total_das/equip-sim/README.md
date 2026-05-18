# equip-sim

산업 프로토콜 시뮬레이터 + Node-RED 라인 DAS.
**1 라인 = 9 설비 + Node-RED 1개**, 같은 `docker-compose.yml` 을 `.env.lineN` 으로
띄워 한 호스트에서 **최대 3 라인 동시 실행** 가능합니다.

```
[CAST-01 (MC Protocol)] ┐
[CNC-01/02/03 (RTU)]    │
[WASH-01 (Modbus TCP)]  ├──▶ [Node-RED 라인 DAS]
[ASSY-01/02 (OPC UA)]   │      ├ 1초 폴링·집계
[TEST-01/02 (OPC UA)]   ┘      └ OPC UA Server  :4860 / :4960 / :5060
                                   ns=2;s=<LINE_ID>.payload (JSON 문자열)
                                   ns=2;s=<LINE_ID>.<EQ>.data.<tag>
                                                              └──▶ 통합-DAS
```

설계 원칙:
- 과설계 금지 — 라인 1개 = compose 1 묶음 + `.env` 1개
- 웹 UI / DB / 인증 / REST API 없음 (모두 stdout 로그)
- SIGINT/SIGTERM graceful shutdown
- dataclass + asyncio (필요한 곳에만)
- **progress 는 단순 센서**. 누적/cycle_time 계산은 Node-RED 책임.

---

## 디렉터리 구조

```
equip-sim/
├── docker-compose.yml             9 설비 + nodered 1개 (env 변수화)
├── .env.line01 / line02 / line03  라인별 LINE_ID + 포트 + project 이름
├── scripts/up.sh / up.ps1         헬퍼 (build flow + compose up)
├── Dockerfile                     시뮬레이터 이미지 (python:3.11-slim)
├── Dockerfile.nodered             Node-RED + contrib (mcprotocol 패치)
├── Dockerfile.serialbridge        socat 가상 시리얼 페어 컨테이너
├── main.py                        시뮬레이터 엔트리
├── requirements.txt
├── configs/                       ★ 라인별 설비 config
│   ├── _generate.py               configs/line{1,2,3}/*.json 재생성기
│   ├── line1/  9 .json
│   ├── line2/  9 .json
│   └── line3/  9 .json
├── sim/
│   ├── config.py / state.py / log.py
│   └── protocols/
│       ├── modbus_server.py        WASH (TCP), CNC (RTU)
│       ├── mc_server.py            CAST (3E Binary)
│       └── opcua_server.py         ASSY / TEST
├── docs/
│   └── integration_spec.md
└── nodered/
    ├── build_flow_das.py
    ├── build_flow_host_viewer.py
    └── flows_das.json (빌드 결과)
```

---

## 라인 / 포트 / 프로토콜

### 라인 메타

| 라인 | LINE_ID | LINE_DIR | Node-RED UI | DAS OPC UA |
|---|---|---|---|---|
| 1 | `LINE-01` | `line1` | **2880** | **4860** |
| 2 | `LINE-02` | `line2` | **3880** | **4960** |
| 3 | `LINE-03` | `line3` | **4880** | **5060** |

### 설비 프로토콜

| 설비 | 인스턴스 | 프로토콜 | 비고 |
|---|---|---|---|
| 주조기 | CAST-01 | **MC Protocol 3E Binary** | TCP |
| 가공기 | CNC-01, CNC-02, CNC-03 | **Modbus RTU** | **시리얼 (가상 시리얼 페어)** |
| 세척기 | WASH-01 | **Modbus TCP** | |
| 조립기 | ASSY-01, ASSY-02 | **OPC UA** | TCP |
| 검사기 | TEST-01, TEST-02 | **OPC UA** | TCP |

### 호스트 포트 (TCP 만, base + 100 × (N-1))

| 설비 | 프로토콜 | LINE-01 | LINE-02 | LINE-03 |
|---|---|---|---|---|
| CAST-01 | MC Protocol | **5001** | **5101** | **5201** |
| WASH-01 | Modbus TCP | 5021 | 5121 | 5221 |
| ASSY-01 | OPC UA | 4841 | 4941 | 5041 |
| ASSY-02 | OPC UA | 4842 | 4942 | 5042 |
| TEST-01 | OPC UA | 4851 | 4951 | 5051 |
| TEST-02 | OPC UA | 4852 | 4952 | 5052 |
| 라인 DAS | OPC UA Server | **4860** | **4960** | **5060** |
| Node-RED UI | HTTP | **2880** | **3880** | **4880** |

> CNC-01/02/03 은 **TCP 포트 사용 안 함**. 시리얼 디바이스로 통신 (아래 §시리얼 참조).
> 이전 명세의 LINE-03 가 4000번대로 떨어졌던 부분은 폐기. 모든 라인 동일하게 48xx → 49xx → 50xx 로 100 씩 증가.

### CNC (Modbus RTU) 시리얼 디바이스

CNC 는 포트가 아니라 시리얼 라인입니다. 도커 안에서 **socat 가상 시리얼 페어** 컨테이너가 `pty` 두 짝을 만들고, 한 쪽은 시뮬레이터(RTU 슬레이브) 가, 다른 쪽은 Node-RED(RTU 마스터) 가 잡습니다.

| 설비 | 시뮬레이터 쪽 (slave) | Node-RED 쪽 (master) | baudrate / parity / stopbits |
|---|---|---|---|
| CNC-01 | `/dev/vserial/cnc01.slave` | `/dev/vserial/cnc01.master` | 9600 / N / 1, slave id 1 |
| CNC-02 | `/dev/vserial/cnc02.slave` | `/dev/vserial/cnc02.master` | 9600 / N / 1, slave id 1 |
| CNC-03 | `/dev/vserial/cnc03.slave` | `/dev/vserial/cnc03.master` | 9600 / N / 1, slave id 1 |

> 페어는 `serial-bridge` 컨테이너의 docker volume (`/dev/vserial`) 을 공유해 시뮬/노드레드 양쪽에서 본다. 라인끼리는 `COMPOSE_PROJECT_NAME` 으로 격리되어 충돌 없음.

---

## 태그 명세 (2026-05-13 개정 3)

### 공통 태그 (모든 설비 9 개)

| 태그 | 단위 | 타입 | RW | 비고 |
|---|---|---|---|---|
| `power` | — | bool | RW | ON/OFF 제어 |
| `progress` | — | float | RO | **단위 시간당 진행률**. base = `1/cycle_sec`, stddev = base × 10% |

설비별 `cycle_sec` (progress base):

| 설비 | cycle_sec | progress base |
|---|---|---|
| CAST-01 | 60 | 1/60 ≈ 0.01667 |
| CNC-01/02/03 | 180 | 1/180 ≈ 0.00556 |
| WASH-01 | 60 | 1/60 |
| ASSY-01/02 | 120 | 1/120 |
| TEST-01/02 | 120 | 1/120 |

> **`cycle_time` 은 센서가 아닙니다.** 시뮬은 매 tick `progress = base ± N(0, stddev)` 만 송출.
> Node-RED 라인 DAS 가 `progress` 를 누적해 `≥ 1.0` 이 되는 순간 직전 cycle 의 소요(초)를 `cycle_time` 으로 산출 → 페이로드의 `data.cycle_time` 으로 발행합니다.

### 설비별 공정 태그

**CAST-01 주조기**
| 태그 | 단위 | 타입 | 범위 / 비고 |
|---|---|---|---|
| `injection_pressure` | MPa | float | 30~120 |
| `mold_temperature` | ℃ | float | 190~240 |
| `cooling_flow` | L/min | float | 20~60 |
| `injection_pressure_sp` | MPa | float | RW |
| `mold_temperature_sp` | ℃ | float | RW |
| `cooling_flow_sp` | L/min | float | RW |

**CNC-01 / 02 / 03 가공기 (Modbus RTU)**
| 태그 | 단위 | 타입 | 범위 / 비고 |
|---|---|---|---|
| `spindle_speed` | rpm | int | 3000~8000 |
| `tool_usage` | % | float | 0~80 |
| `coolant_flow` | L/min | float | 10~30 |
| `spindle_speed_sp` | rpm | int | RW |
| `tool_usage_sp` | % | float | RW |
| `coolant_flow_sp` | L/min | float | RW |

**WASH-01 세척기 (Modbus TCP)**
| 태그 | 단위 | 타입 | 범위 / 비고 |
|---|---|---|---|
| `cleaning_concentration` | % | float | 2~5 |
| `cleaning_temperature` | ℃ | float | 50~75 |
| `cleaning_pressure` | bar | float | 2~6 |
| `cleaning_concentration_sp` | % | float | RW |
| `cleaning_temperature_sp` | ℃ | float | RW |
| `cleaning_pressure_sp` | bar | float | RW |

**ASSY-01 / 02 조립기 (OPC UA)**
| 태그 | 단위 | 타입 | 범위 / 비고 |
|---|---|---|---|
| `tightening_torque` | Nm | float | 30~50 |
| `tightening_angle` | deg | float | 설정 범위 내 |
| `press_force` | N | float | 500~3000 |
| `tightening_torque_sp` | Nm | float | RW |
| `tightening_angle_sp` | deg | float | RW |
| `press_force_sp` | N | float | RW |

**TEST-01 / 02 검사기 (OPC UA)**
| 태그 | 단위 | 타입 | 범위 / 비고 |
|---|---|---|---|
| `bore_dimension` | mm | float | 40.000 ± 0.020 |
| `hole_dimension` | mm | float | 10.200 ± 0.050 |
| `result_ok` | bool | bool | RO. OK=true / NG=false |
| `bore_dimension_sp` | mm | float | RW |
| `hole_dimension_sp` | mm | float | RW |

### 프로토콜별 매핑

**MC Protocol 3E Binary — CAST-01 전용**
| 주소 | 태그 | 타입 |
|---|---|---|
| `M0` | `power` | bit |
| `D0` | `injection_pressure_sp` | float (LSB-first, 2 word) |
| `D2` | `mold_temperature_sp` | float |
| `D4` | `cooling_flow_sp` | float |
| `D100` | `injection_pressure` | float |
| `D102` | `mold_temperature` | float |
| `D104` | `cooling_flow` | float |
| `D106` | `progress` | float |

**Modbus RTU — CNC-01 / 02 / 03 (slave id 1)**
| Function | Address | 태그 | 타입 |
|---|---|---|---|
| Coil (FC01/05) | `0` | `power` | bool |
| HR (FC03/06) | `0` | `spindle_speed_sp` | uint16 |
| HR | `2` | `spindle_speed` | uint16 |
| HR | `1000` | `tool_usage_sp` | float (2 word, big-endian) |
| HR | `1002` | `tool_usage` | float |
| HR | `1004` | `coolant_flow_sp` | float |
| HR | `1006` | `coolant_flow` | float |
| HR | `1008` | `progress` | float |

**Modbus TCP — WASH-01**
| Function | Address | 태그 | 타입 |
|---|---|---|---|
| Coil | `0` | `power` | bool |
| HR | `1000` | `cleaning_concentration_sp` | float |
| HR | `1002` | `cleaning_temperature_sp` | float |
| HR | `1004` | `cleaning_pressure_sp` | float |
| HR | `1006` | `cleaning_concentration` | float |
| HR | `1008` | `cleaning_temperature` | float |
| HR | `1010` | `cleaning_pressure` | float |
| HR | `1012` | `progress` | float |

**OPC UA — ASSY / TEST**
`ns=2;s=<EquipmentName>.<tag>` 평면 구조. EquipmentName 은 config 의 `equipment_name` (예: `LINE-01_ASSY-01`).

---

## 라인 DAS → 통합-DAS 페이로드

라인 DAS Node-RED 가 1 초에 1 회 다음 JSON 을 만들어 OPC UA Server 의 `payload` 변수로 발행합니다.

```json
{
  "ts": "2026-05-13T05:50:12.345Z",
  "line_id": "LINE-01",
  "schema_version": "1.0",
  "equipments": {
    "CAST-01": {
      "status": "RUN",
      "ts": "2026-05-13T05:50:12.300Z",
      "quality": "GOOD",
      "data": {
        "power": true,
        "progress": 0.0168,
        "cycle_time": 59.8,
        "injection_pressure": 65.4,
        "mold_temperature": 215.3,
        "cooling_flow": 38.2,
        "injection_pressure_sp": 80.0,
        "mold_temperature_sp": 215.0,
        "cooling_flow_sp": 40.0
      }
    },
    "CNC-01": { "...": "..." },
    "WASH-01": { "...": "..." },
    "ASSY-01": { "...": "..." },
    "TEST-01": { "...": "..." }
  }
}
```

### 필드 정의

| 키 | 타입 | 설명 |
|---|---|---|
| `ts` | string | ISO-8601 UTC ms. 라인 DAS 페이로드 생성 시각 |
| `line_id` | string | `LINE-01 / LINE-02 / LINE-03` |
| `schema_version` | string | 본 규격 버전 (현재 `1.0`) |
| `equipments.<ID>.status` | string | `OFF / RUN`. (`WARNING / DANGER` 향후 확장 자리) |
| `equipments.<ID>.ts` | string | 해당 설비 데이터 측정 시각 |
| `equipments.<ID>.quality` | string | `GOOD / UNCERTAIN / BAD` |
| `equipments.<ID>.data` | object | 태그명 → 값. 공통 + 공정 태그 평탄 구조 |
| `equipments.<ID>.data.cycle_time` | float | 라인 DAS 가 progress 누적으로 산출. 1회도 완료 전이면 키 없음 |

### status / quality / cycle_time 판정 (Node-RED 책임)

시뮬레이터는 절대 `status`, `quality`, `cycle_time` 같은 파생값을 만들지 않습니다.

**status**
1. 데이터 없음 또는 통신 실패 → `OFF` + `quality=BAD`
2. `power === false` → `OFF`
3. 그 외 → `RUN`
4. `WARNING / DANGER` 는 명세상 자리만 잡혀 있고 현재 구현은 OFF/RUN 두 단계

**quality**
| 값 | 조건 |
|---|---|
| `GOOD` | 마지막 수신이 3 초 이내 |
| `UNCERTAIN` | 마지막 수신이 3 초 초과, 그러나 일부 데이터는 존재 |
| `BAD` | 데이터 없음 (폴링 자체 실패) |

**cycle_time**
- Node-RED flow context: `cycle_state[<EQ>] = { acc, last_cycle_time, start_ms }`
- 매 tick `acc += progress`. `start_ms` 가 없으면 `Date.now()` 로 초기화.
- `acc >= 1.0` 인 순간 cycle 1 회 완료 →
  `last_cycle_time = (Date.now() - start_ms) / 1000`, `acc=0`, `start_ms=null` 리셋.
- 페이로드의 `data.cycle_time` 은 `last_cycle_time`. 한 번도 완료 전이면 키 생략.

---

## 라인 DAS → 통합-DAS OPC UA 노출

### Endpoint

| 라인 | Endpoint |
|---|---|
| LINE-01 | `opc.tcp://<host>:4860/line-das/LINE-01` |
| LINE-02 | `opc.tcp://<host>:4960/line-das/LINE-02` |
| LINE-03 | `opc.tcp://<host>:5060/line-das/LINE-03` |

### NodeId 규약

```
ns=2;s=<line_id>.payload                                (String, 통째 JSON)
ns=2;s=<line_id>.line_ts                                (String)
ns=2;s=<line_id>.schema_version                         (String)
ns=2;s=<line_id>.<equipment_id>.status                  (String)
ns=2;s=<line_id>.<equipment_id>.ts                      (String)
ns=2;s=<line_id>.<equipment_id>.quality                 (String)
ns=2;s=<line_id>.<equipment_id>.data.<tag_name>         (Boolean / Int32 / Double)
ns=2;s=<line_id>.<equipment_id>.data.cycle_time         (Double)
```

> `node-red-contrib-opcua` 의 OpcUa-Server 는 **2 단계 프로토콜** 사용:
> 1. 최초 1회: `msg.topic = 'ns=2;s=<name>;datatype=<DT>'` + `msg.payload = { opcuaCommand: 'addVariable' }`
> 2. 이후 매 tick: `msg.payload = { messageType: 'Variable', namespace: 2, variableName: '<name>', variableValue: <val>, datatype: '<DT>' }`
>
> 필드가 하나라도 빠지면 `"warning: properties like messageType, namespace, variableName or VariableValue is missing."` 경고가 매 메시지마다 발생.

---

## 통합 DAS (3개 라인 머지)

3개 라인 DAS 의 페이로드를 한 컨테이너로 모으는 **통합 DAS** (`nodered-das`) 가 별도로 떠 있습니다. 이 컨테이너는 `factory-net` 외부 도커 네트워크에 attach 되어 있어서, 각 라인 컨테이너 (`nodered-line01/02/03`) 의 OPC UA Server 를 **컨테이너명으로** 안정적으로 접근합니다.

### 컨테이너 / 포트

| 항목 | 값 |
|---|---|
| 컨테이너명 | `nodered-das` |
| compose 파일 | `integration/docker-compose.yml` |
| Node-RED UI | `http://localhost:5880` |
| OPC UA Server (재노출) | `opc.tcp://localhost:5860/integration-das` |
| 네트워크 | `factory-net` (external, 사전 생성 필요) |

### Flow 구조

`integration/build_flow_integration.py` 가 생성하는 `flows_integration.json` 는 라인별로 다음 컬럼을 갖습니다:

```
[1s inject] → [OpcUa-Item: ns=2;s=LINE-0X.payload] → [OpcUa-Client READ]
              → [PARSE: JSON.parse] → [DEBUG (stdout)]
                                    → [PUBLISH (addVariable+Variable)] → [OpcUa-Server :5860]
```

각 라인 DAS 의 endpoint:

| 라인 | 통합 DAS 가 읽는 endpoint |
|---|---|
| LINE-01 | `opc.tcp://nodered-line01:4860/line-das/LINE-01` |
| LINE-02 | `opc.tcp://nodered-line02:4960/line-das/LINE-02` |
| LINE-03 | `opc.tcp://nodered-line03:5060/line-das/LINE-03` |

> 라인 compose 의 `nodered` 서비스는 `container_name: nodered-line0X` + `factory-net` alias 가 박혀 있어서, 통합 DAS 가 IP 가 아닌 컨테이너명으로 안정 접속.

### 통합 DAS 가 재노출하는 NodeId

```
ns=2;s=LINE-01.payload            (원본 JSON String)
ns=2;s=LINE-01.<EQ>.status         (String)
ns=2;s=LINE-01.<EQ>.data.<tag>    (Boolean / Int32 / Double)
... (LINE-02, LINE-03 도 동일하게)
```

호스트의 UA Expert 같은 클라이언트는 `opc.tcp://localhost:5860/integration-das` 한 곳만 보면 **3개 라인 전체 텔레메트리** 가 평면 NodeId 로 다 보입니다.

---

## 폴링 / 주기

| 항목 | 값 | 비고 |
|---|---|---|
| 설비 측 sampling_ms | 1000 | 시뮬레이터 내부 갱신 주기 |
| 라인 DAS 폴링 주기 | 1000 | Node-RED inject |
| 페이로드 생성 주기 | 1000 | 라인 단위 1초 1건 |
| 통신 timeout | 10000 | 설비 응답 대기 |

---

## 빠르게 실행

### 0) 사전 준비

- Docker Desktop (Windows / macOS) 또는 docker engine + compose v2 (Linux)
- Python 3.11+ (flow 빌드용)
- (선택) Node.js + Node-RED 4.x — 호스트 viewer 용

### ✨ 일괄 기동 (권장)

3개 라인 + 통합 DAS 을 한 번에 올릴 때는 `up-all` 스크립트가 가장 간단합니다. 내부적으로 factory-net 생성 + flow 빌드 + 4개 compose up 을 순서대로 수행합니다.

**PowerShell**:
```powershell
.\scripts\up-all.ps1            # 전체 기동
.\scripts\up-all.ps1 down       # 전체 종료
.\scripts\up-all.ps1 logs -Line LINE-02
.\scripts\up-all.ps1 ps
```

**Bash**:
```bash
./scripts/up-all.sh             # 전체 기동
./scripts/up-all.sh down        # 전체 종료
./scripts/up-all.sh logs LINE-02
./scripts/up-all.sh ps
```

전체 기동되면 아래 포트가 열립니다:

| 항목 | 주소 |
|---|---|
| LINE-01 Node-RED UI | <http://localhost:2880> |
| LINE-02 Node-RED UI | <http://localhost:3880> |
| LINE-03 Node-RED UI | <http://localhost:4880> |
| 통합 DAS Node-RED UI | <http://localhost:5880> |
| 통합 DAS OPC UA | `opc.tcp://localhost:5860/integration-das` |

개별로 돌리고 싶으면 아래 1–2단계를 따라가세요.

### 1) Node-RED flow 빌드

flow 는 라인별로 다른 포트를 박아두므로 라인 바꿀 때마다 다시 빌드합니다.

**PowerShell**:
```powershell
$env:LINE_ID = "LINE-01"
python nodered\build_flow_das.py --host-mode docker
```

**Bash**:
```bash
LINE_ID=LINE-01 python nodered/build_flow_das.py --host-mode docker
```

### 2) 라인 기동

**한 줄 헬퍼**:
```bash
./scripts/up.sh LINE-01           # Linux / macOS / Git Bash
.\scripts\up.ps1 LINE-01          # PowerShell
```

**직접 compose 호출**:
```bash
docker compose --env-file .env.line01 up -d --build
docker compose --env-file .env.line02 up -d --build
docker compose --env-file .env.line03 up -d --build
```

각 라인은 다음 컨테이너를 띄웁니다:
- `serial-bridge` — socat 가상 시리얼 페어 (`/dev/vserial/cnc0{1,2,3}.{slave,master}`)
- `cast-01`, `cnc-01/02/03`, `wash-01`, `assy-01/02`, `test-01/02` — 시뮬레이터 (9 개)
- `nodered` — 라인 DAS

`COMPOSE_PROJECT_NAME` 이 라인마다 달라서 네트워크/볼륨/디바이스가 완전히 격리됩니다.

### 3) flow import

| 라인 | admin UI | DAS endpoint |
|---|---|---|
| LINE-01 | <http://localhost:2880> | `opc.tcp://localhost:4860/line-das/LINE-01` |
| LINE-02 | <http://localhost:3880> | `opc.tcp://localhost:4960/line-das/LINE-02` |
| LINE-03 | <http://localhost:4880> | `opc.tcp://localhost:5060/line-das/LINE-03` |

Node-RED admin UI → 우상단 메뉴 → **Import** → `/host-flows/flows_das.json` → **Deploy**.

### 4) 동작 확인

- Node-RED debug 사이드바에 1 초마다 `payload` 출력
- DAS OPC UA Server 노드:
  - `ns=2;s=<LINE>.payload` — 통째 JSON (String)
  - `ns=2;s=<LINE>.line_ts`, `schema_version`
  - `ns=2;s=<LINE>.<EQ>.status / ts / quality`
  - `ns=2;s=<LINE>.<EQ>.data.<tag>` — Boolean / Int32 / Double
  - `ns=2;s=<LINE>.<EQ>.data.cycle_time` — Double (라인 DAS 산출)

### 5) 종료

```bash
./scripts/up.sh LINE-01 down         # 컨테이너만
./scripts/up.sh LINE-01 down -v      # nodered-data 볼륨까지
```

---

## 윈도우 환경

PowerShell 에서는 `LINE_ID=... 명령` 같은 inline env 가 안 됩니다. 두 줄로 나눠 쓰세요:

```powershell
cd C:\path\to\equip-sim

# LINE-01
$env:LINE_ID = "LINE-01"
python nodered\build_flow_das.py --host-mode docker
docker compose --env-file .env.line01 up -d --build

# LINE-02
$env:LINE_ID = "LINE-02"
python nodered\build_flow_das.py --host-mode docker
docker compose --env-file .env.line02 up -d --build

# LINE-03
$env:LINE_ID = "LINE-03"
python nodered\build_flow_das.py --host-mode docker
docker compose --env-file .env.line03 up -d --build
```

또는 `scripts\up.ps1 LINE-01` 한 줄로.

### 윈도우에서 주의

1. `localhost` 가 IPv6 (`::1`) 로 풀려서 OPC UA 클라이언트가 못 붙는 케이스가 있음 → endpoint URL 을 `127.0.0.1` 로 사용
2. Docker Desktop 의 컨테이너 hostname 이 외부에 advertise 되면 OPC UA discovery 에서 `Server end point are not known yet` 발생 → docker-compose 의 nodered 서비스에 `hostname: localhost` 추가 (이미 빌더가 박아둠)
3. PowerShell 실행 정책 막히면 한 세션 한정: `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`
4. 처음 띄울 때 Windows Defender 가 "docker / python 이 네트워크 사용 허용?" 묻는데 허용

---

## 호스트 Node-RED 에서 조회 (포트 1883)

도커 안의 라인 DAS 가 `4860 / 4960 / 5060` 으로 노출한 OPC UA 변수를 호스트 OS 의 별도 Node-RED (포트 **1883**) 에서 구독해 확인하는 용도.

### 1) 호스트 Node-RED 설치 + 1883 으로 기동

```powershell
npm install -g --unsafe-perm node-red

cd $env:USERPROFILE\.node-red
npm install node-red-contrib-opcua

node-red -p 1883
```

### 2) viewer flow 빌드

```bash
python nodered/build_flow_host_viewer.py
# wrote nodered/flows_host_viewer.json (26 nodes) lines=['LINE-01','LINE-02','LINE-03'] host=127.0.0.1

# 다른 PC 의 도커에 붙으려면:
python nodered/build_flow_host_viewer.py --host 192.168.0.10

# 특정 라인만:
python nodered/build_flow_host_viewer.py --lines LINE-01
```

### 3) Node-RED 1883 에서 import

<http://localhost:1883> → 우상단 메뉴 → Import → `nodered/flows_host_viewer.json` → Deploy.

2 초 뒤 각 라인의 subscribe 가 시작되고 debug 사이드바에 `LINE-01 payload` 같은 이름으로 파싱된 JSON 객체가 1초마다 뜨면 성공.

### 4) 개별 태그도 구독

각 라인의 `LINE-0X topics` function 노드 `EXTRA_TOPICS` 배열에 추가:

```js
const EXTRA_TOPICS = [
  { name: `${LINE}.CAST-01.data.progress`,     datatype: 'Double'  },
  { name: `${LINE}.CNC-01.data.spindle_speed`, datatype: 'Int32'   },
  { name: `${LINE}.TEST-01.data.result_ok`,    datatype: 'Boolean' },
];
```

### 5) 주의

- **도커 쪽 flow 가 먼저 Deploy 되어 있어야 함** (addVariable 명령이 돌아 변수들이 등록된 이후에 구독 가능)
- `localhost` 로 안 붙으면 `127.0.0.1` 로 재빌드 (IPv6 이슈)
- 호스트 Node-RED 의 1883 은 MQTT 기본 포트와 같은 숫자이지만 여기서는 admin UI 용 — 충돌 아님

---

## flow 검증 / 단위 테스트

빌드 직후 자동 회귀 테스트:

```bash
LINE_ID=LINE-01 python nodered/build_flow_das.py --host-mode docker
python nodered/_verify_flow_das.py        # wires/ref/JS syntax 정적 검증
node nodered/_test_emit_payload.js        # status / quality / cycle_time 판정
node nodered/_test_publish.js             # NodeId / dtype / addVariable 2단계
```

기대 결과: 모두 `OK` / `ALL ASSERTIONS PASSED`.

---

## Node-RED 이미지 (Dockerfile.nodered)

`nodered/node-red:4.1.10-22` 베이스 + contrib:
- `node-red-contrib-modbus` (WASH-TCP, CNC-RTU)
- `node-red-contrib-opcua` (ASSY/TEST + DAS Server)
- `node-red-contrib-mcprotocol` (CAST-01)

> ⚠️ Node 24+ 에서 `mcprotocol` 의 `util.log()` 호출이 깨집니다.
> 빌드 시 `sed` 로 `console.log(` 으로 치환 (`Dockerfile.nodered` 참고).

---

## 시뮬레이터 컨테이너

- 1 컨테이너 = 1 설비 = `SIM_CONFIG=/app/configs/<line_dir>/<EQ>.json`
- config 안의 `equipment_name` 은 `"${LINE_ID:-LINE-00}_<EQ>"` 토큰 보관 → 기동 시 치환
- 동작:
  - **MC Protocol** (CAST-01) → 3E Binary, M=bit, D=int/float (LSB-first 2-word)
  - **Modbus TCP** (WASH-01) → Coil + HR (big-endian 2-word float)
  - **Modbus RTU** (CNC) → socat 가상 시리얼 슬레이브, slave id 1, 9600/N/1
  - **OPC UA** (ASSY/TEST) → `ns=2;s=<EquipmentName>.<tag>` 평면 구조

CNC 시뮬레이터는 시리얼 디바이스 `/dev/vserial/cnc0X.slave` 가 마운트되어 있어야 기동되므로, 항상 `serial-bridge` 컨테이너에 `depends_on` 으로 묶여 있습니다.

---

## 트러블슈팅

- **mcprotocol 노드가 빨강** — Node-RED 컨테이너를 다시 빌드 (`Dockerfile.nodered` 의 `util.log` 패치 미적용 베이스에서 띄운 경우)
- **OPC UA Server 가 4860 으로 안 열림** — Node-RED 컨테이너가 해당 라인 DAS 포트를 publish 했는지, flow 의 `OpcUa-Server` 노드가 deploy 되었는지 확인
- **`Server end point are not known yet`** — 컨테이너 hostname 이 외부에서 안 풀려서. docker-compose 의 nodered 서비스에 `hostname: localhost` 또는 호스트 IP advertise 설정 필요
- **`equipment_name` 이 `${LINE_ID:-...}` 그대로 노출** — `sim/config.py` 가 치환 안 된 상태. `python -c "from sim.config import load_config; print(load_config('configs/line1/CAST-01.json').equipment_name)"` 로 확인
- **포트 충돌** — 같은 라인을 두 번 띄웠거나, 다른 프로그램이 점유.
  ```powershell
  Get-NetTCPConnection -LocalPort 2880,3880,4880,4860,4960,5060 -ErrorAction SilentlyContinue
  ```
- **flow 가 옛날 포트로 접속 시도** — 라인을 바꾸고 flow 를 재빌드 안 함. `LINE_ID=LINE-0X python nodered/build_flow_das.py --host-mode docker` 후 import 다시
- **CNC RTU 가 응답 없음** — `serial-bridge` 컨테이너가 떠 있는지 확인. 컨테이너 안에서 `ls /dev/vserial/cnc01.*` 가 보여야 정상. 도커 볼륨 권한이 막혔으면 `serial-bridge` 의 init 로그 확인
- **OpcUa-Server 경고 "messageType / namespace / variableName / variableValue missing"** — PUBLISH function 이 옛 메시지 포맷. 새 빌더는 addVariable + Variable 2단계로 발행 (위 NodeId 규약 박스 참조). flow 재빌드 + 재 deploy
