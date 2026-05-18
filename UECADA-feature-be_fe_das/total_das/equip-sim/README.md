# equip-sim

## Local total_DAS wiring

Each line still runs as its own Compose project with its own `.env.lineNN`, but
only the line Node-RED container is attached to the shared external network
`total-das-net`. The simulator containers stay on a per-line internal network so
service names such as `cast-01` do not collide across lines.

Plain `docker compose up` in this folder reads `.env` and starts LINE-01 by
default. Use `.env.line02` or `.env.line03` when you want the other lines.

On Windows, use:

```powershell
.\scripts\up.ps1 LINE-01
.\scripts\up.ps1 LINE-02
.\scripts\up.ps1 LINE-03
```

The wrapper creates `total-das-net` if needed, validates the matching fixed
flow file, and starts only that line's Compose project. Docker Compose always
uses `nodered/flows_das_LINE-01.json`, `nodered/flows_das_LINE-02.json`, or
`nodered/flows_das_LINE-03.json` as `/data/flows.json` inside Node-RED; it does
not rebuild or overwrite those files during `up`.

산업 프로토콜 시뮬레이터 + Node-RED 라인 DAS.
**1 라인 = 9 설비 + Node-RED 1개**, 같은 `docker-compose.yml` 을 `.env.lineN` 으로
띄워 한 호스트에서 **최대 3 라인 동시 실행** 가능합니다.

```
[CAST-01 (Modbus)]    ┐
[CNC-01 (MC Protocol)]│
[CNC-02/03 (OPC UA)]  ├─→ [Node-RED 컨테이너]
[WASH-01 (OPC UA)]    │      ├ 1초 폴링·집계
[ASSY-01/02 (OPC UA)] │      └ OPC UA Server  :4870 / :4970 / :5070
[TEST-01/02 (OPC UA)] ┘            ns=2;s=<LINE_ID>.payload (JSON 문자열)
                                   ns=2;s=<LINE_ID>.<EQ>.data.<tag>
                                                              └→ X-DAS 가 구독
```

설계 원칙:
- 과설계 금지 — `라인 1개 = compose 1 묶음 + .env 1개`
- 웹 UI / DB / 인증 / REST API 없음 (모두 stdout 로그)
- SIGINT/SIGTERM graceful shutdown
- dataclass + asyncio (필요한 곳에만)
- **progress 는 단순 센서**. 누적/cycle_time 계산은 Node-RED 책임.

---

## 디렉터리 구조

```
equip-sim/
├── docker-compose.yml         9 설비 + nodered 1개 (env 변수화)
├── .env.line01 / line02 / line03   라인별 LINE_ID + 포트 + project 이름
├── scripts/up.sh              헬퍼 (build flow + compose up)
├── Dockerfile                 시뮬레이터 이미지 (python:3.11-slim)
├── Dockerfile.nodered         Node-RED 4.1.10-22 + contrib + mcprotocol 패치
├── main.py                    시뮬레이터 엔트리
├── requirements.txt
├── configs/                   ★ 라인별 설비 config
│   ├── _generate.py           configs/line{1,2,3}/*.json 재생성기
│   ├── line1/  9 .json        LINE-01 설비 9개
│   ├── line2/  9 .json        LINE-02
│   └── line3/  9 .json        LINE-03
├── sim/
│   ├── config.py              ${LINE_ID:-LINE-00} 같은 env 토큰 치환
│   ├── state.py               progress 같이 SP 없는 센서도 base_value 로 동작
│   ├── log.py
│   └── protocols/
│       ├── modbus_server.py
│       ├── mc_server.py
│       └── opcua_server.py
├── docs/
│   └── integration_spec.md    페이로드/NodeId/포트 명세
└── nodered/
    ├── build_flow_das.py      라인 DAS flow 빌더 (LINE_ID env)
    ├── flows_das.json         빌드 결과
    ├── _verify_flow_das.py    정적 검증 (wires/JS syntax)
    ├── _test_emit_payload.js  status/quality/cycle_time 단위 테스트
    └── _test_publish.js       NodeId 매핑/dtype 단위 테스트
```

---

## 라인 / 포트 / 프로토콜

| 라인 | LINE_ID | LINE_DIR | UI | DAS OPCUA |
|---|---|---|---|---|
| 1 | `LINE-01` | `line1` | 1880 | 4870 |
| 2 | `LINE-02` | `line2` | 1881 | 4970 |
| 3 | `LINE-03` | `line3` | 1882 | 5070 |

**설비 포트는 `base + 100 × (N-1)`** 정책으로 라인끼리 충돌하지 않습니다.

| 설비 | 프로토콜 | LINE-01 | LINE-02 | LINE-03 |
|---|---|---|---|---|
| CAST-01 | Modbus TCP | 5021 | 5121 | 5221 |
| CNC-01 | **MC Protocol 3E Binary** | 5081 | 5181 | 5281 |
| CNC-02 | OPC UA | 5082 | 5182 | 5282 |
| CNC-03 | OPC UA | 5083 | 5183 | 5283 |
| WASH-01 | OPC UA | 4841 | 4941 | 5041 |
| ASSY-01 | OPC UA | 4851 | 4951 | 5051 |
| ASSY-02 | OPC UA | 4852 | 4952 | 5052 |
| TEST-01 | OPC UA | 4861 | 4961 | 5061 |
| TEST-02 | OPC UA | 4862 | 4962 | 5062 |

> **변경 사항**: 이전 버전에서는 CNC-01/02/03 모두 MC Protocol 이었지만,
> 현재는 **CNC-01 만 MC Protocol**, CNC-02/03 은 OPC UA 로 통일되었습니다.

---

## 태그 명세 (2026-05 개정판)

### 공통 (모든 설비 9개)

| 태그 | 타입 | RW | 의미 |
|---|---|---|---|
| `power` | bool | RW | 설비 가동 상태 |
| `progress` | float | RO | **단위 시간당 진행률**. base = `1/cycle_sec`, stddev = base × 10% |

설비별 `cycle_sec`:

| 설비 | cycle_sec | progress base |
|---|---|---|
| CAST-01 | 60 | 1/60 ≈ 0.01667 |
| CNC-01/02/03 | 180 | 1/180 ≈ 0.00556 |
| WASH-01 | 60 | 1/60 |
| ASSY-01/02 | 120 | 1/120 |
| TEST-01/02 | 120 | 1/120 |

> 시뮬레이터는 **매 tick `base ± N(0, stddev)` 만 송출**합니다.
> 1.0 을 넘으면 cycle 1회 완료라는 의미이며, 그 누적/리셋/cycle_time 산출은
> Node-RED EMIT PAYLOAD function 에서 flow context 로 관리합니다.

### 설비별 고유 태그

- **CAST-01**: `injection_pressure`, `mold_temperature`, `cooling_flow`,
  `injection_pressure_sp` (RW), `mold_temperature_sp` (RW)
- **CNC-01/02/03**: `spindle_speed`, `tool_usage`, `coolant_flow`,
  `spindle_speed_sp` (RW)
- **WASH-01**: `cleaning_concentration`, `temperature`, `pressure`, `flow`,
  `cleaning_temperature_sp` (RW)
- **ASSY-01/02**: `tightening_torque`, `angle`, `press_force`,
  `part_detected` (bool), `tightening_torque_sp` (RW)
- **TEST-01/02**: `bore_dimension`, `hole_dimension`, `leak_rate`,
  `flow_value`, `result_ok` (bool) — *SP 없음*

> **제거된 태그**: `voltage`, `current`, `surface_temperature`, `vibration`,
> `alarm`, `part_count`, `cycle_time` (센서로서). 모두 명세 개정으로 빠졌습니다.

### MC Protocol 매핑 (CNC-01 전용)

| 주소 | 태그 | 타입 |
|---|---|---|
| `M0` | `power` | bool |
| `D0` | `spindle_speed_sp` | float (2 word, LSB-first) |
| `D2` | `spindle_speed` | float |
| `D100` | `tool_usage` | float |
| `D102` | `coolant_flow` | float |
| `D104` | `progress` | float |

OPC UA / Modbus 측 NodeId/주소는 `docs/integration_spec.md` 참고.

---

## 빠르게 실행

### 0) 사전 준비 — Node-RED flow 빌드

flow 는 라인별로 다른 포트를 박아두므로, 라인 바꿀 때마다 다시 빌드합니다.

```bash
cd equip-sim
LINE_ID=LINE-01 python nodered/build_flow_das.py --host-mode docker
# wrote nodered/flows_das.json (165 nodes) line_id=LINE-01 host_mode=docker
```

> 호스트에서 직접 (localhost 포트) 접근하려면 `--host-mode localhost`.

### 1) 한 줄 헬퍼 (권장)

```bash
./scripts/up.sh LINE-01           # = fixed flow check + compose up -d --build
./scripts/up.sh LINE-02 logs -f   # 임의 compose 명령 그대로 전달
./scripts/up.sh LINE-03 down -v
```

### 2) 직접 compose 호출

```bash
docker compose up -d --build
docker compose --env-file .env.line01 up -d --build
docker compose --env-file .env.line02 up -d --build
docker compose --env-file .env.line03 up -d --build
```

- `COMPOSE_PROJECT_NAME` 이 라인마다 다르므로 컨테이너/네트워크/볼륨이 격리됩니다.
- 컨테이너 내부 포트 = 호스트 publish 포트 (1:1).
  단 Node-RED admin UI 만 `${PORT_NODERED_UI}:1880` 매핑.

### 3) flow import

Node-RED admin UI → Import → `./nodered/flows_das.json`
(컨테이너 안에선 `/host-flows/flows_das.json`) → Deploy.

| 라인 | admin UI | DAS endpoint |
|---|---|---|
| LINE-01 | <http://localhost:1880> | `opc.tcp://localhost:4870/line-das/LINE-01` |
| LINE-02 | <http://localhost:1881> | `opc.tcp://localhost:4970/line-das/LINE-02` |
| LINE-03 | <http://localhost:1882> | `opc.tcp://localhost:5070/line-das/LINE-03` |

### 4) 동작 확인

- Node-RED 디버그 사이드바: 1초마다 `payload` 출력
- DAS OPC UA Server 노드:
  - `ns=2;s=<LINE>.payload` (String, 통째 JSON)
  - `ns=2;s=<LINE>.line_ts`, `ns=2;s=<LINE>.schema_version`
  - `ns=2;s=<LINE>.<EQ>.status / ts / quality`
  - `ns=2;s=<LINE>.<EQ>.data.<tag>` (Float / Boolean / Int32)
  - `ns=2;s=<LINE>.<EQ>.data.cycle_time` (Float, Node-RED 가 `progress` 누적으로 산출해 `data` 에 합쳐 발행)

### 5) 종료

```bash
./scripts/up.sh LINE-01 down              # 컨테이너만 제거
./scripts/up.sh LINE-01 down -v           # nodered-data 볼륨까지
```

---

## status / cycle_time 판정 (Node-RED 책임)

시뮬레이터는 절대 `alarm` 이나 `cycle_time` 같은 파생값을 만들지 않습니다.
모두 Node-RED EMIT PAYLOAD function 에서 처리합니다.

- **status**:
  - `power === false` → `OFF`
  - 그 외 → `RUN` (`WARNING` / `DANGER` 는 향후 확장 자리)
- **quality**:
  - 데이터 없음 → `BAD`
  - 마지막 수신 > 3 s → `UNCERTAIN`
  - 그 외 → `GOOD`
- **cycle_time** (flow context `cycle_state`):
  - 매 tick `progress` 를 `acc` 에 더하고, `start_ms` 가 없으면 기록
  - `acc >= 1.0` 가 되는 순간 `cycle_time = (now - start_ms) / 1000`
  - `acc` 와 `start_ms` 리셋 → 다음 cycle 측정 시작

---

## Node-RED 이미지 (`Dockerfile.nodered`)

`nodered/node-red:4.1.10-22` 베이스에 다음 contrib 를 설치합니다:

- `node-red-contrib-modbus` (CAST-01)
- `node-red-contrib-opcua`  (CNC-02/03, WASH/ASSY/TEST + DAS Server)
- `node-red-contrib-mcprotocol` (CNC-01)

> ⚠️ Node 24+ 에서 `mcprotocol` 의 `util.log()` 호출이 깨집니다.
> 빌드 시 `sed` 로 `console.log(` 으로 치환합니다 (`Dockerfile.nodered` 참고).

---

## 시뮬레이터 컨테이너

- 1 컨테이너 = 1 설비 = `SIM_CONFIG=/app/configs/<line_dir>/<EQ>.json`
- config 안의 `equipment_name` 은 `"${LINE_ID:-LINE-00}_<EQ>"` 토큰 보관 → 기동 시 치환
- 동작:
  - Modbus → bool→coil, int→HR[0..], float→HR[1000..] (big-endian, 2 word)
  - MC Protocol → 3E Binary, M=bit, D=int/float (LSB-first 2-word float)
  - OPC UA → `ns=2;s=<EquipmentName>.<tag>` 평면 구조

자세한 페이로드/NodeId 규약은 [`docs/integration_spec.md`](docs/integration_spec.md).

---

## 호스트 Node-RED 에서 조회 (포트 1883)

도커 안의 라인 DAS 가 `4870 / 4970 / 5070` 으로 노출한 OPC UA 변수를,
호스트 OS 에 따로 설치한 Node-RED (포트 **1883**) 에서 subscribe 해 확인하는 용도.

### 1) 호스트 Node-RED 설치 + 1883 으로 기동

```powershell
# Node.js LTS 먼저 설치. 그 다음:
npm install -g --unsafe-perm node-red

# OPC UA 클라이언트 노드 설치 (한 번만)
cd $env:USERPROFILE\.node-red
npm install node-red-contrib-opcua

# 1883 포트로 기동
node-red -p 1883
```

매번 `-p 1883` 치기 귀찮으면 `%USERPROFILE%\.node-red\settings.js` 에서
`uiPort: 1883` 로 설정. 접속: <http://localhost:1883>

### 2) viewer flow 빌드

도커가 `localhost` 에 포트 publish 한 경우:

```bash
python nodered/build_flow_host_viewer.py
# wrote nodered/flows_host_viewer.json (26 nodes) lines=['LINE-01','LINE-02','LINE-03'] host=127.0.0.1
```

다른 PC 의 도커에 붙으려면:

```bash
python nodered/build_flow_host_viewer.py --host 192.168.0.10
```

특정 라인만 보고 싶으면:

```bash
python nodered/build_flow_host_viewer.py --lines LINE-01
```

### 3) Node-RED 1883 에서 import

호스트 Node-RED admin UI 접속 → 우상단 메뉴 → **Import** →
`nodered/flows_host_viewer.json` 파일 선택 → **Deploy**.

2 초 뒤에 각 라인의 `subscribe` 가 시작되고, debug 사이드바에
`LINE-01 payload` 같은 이름으로 **파싱된 JSON 객체** 가 1초마다 뜨면 성공.

```
msg.topic   = "ns=2;s=LINE-01.payload"
msg.payload = {
  ts: "2026-05-13T...",
  line_id: "LINE-01",
  schema_version: "1.0",
  equipments: { "CAST-01": { status: "RUN", quality: "GOOD",
                              data: { power: true, progress: 0.0168, ... } }, ... }
}
```

### 4) 개별 태그도 구독하고 싶으면

각 라인 목엄의 `LINE-0X topics` function 노드 안 `EXTRA_TOPICS` 배열에
노드를 추가하고 Deploy 다시.

```js
const EXTRA_TOPICS = [
  { name: `${LINE}.CAST-01.data.progress`,     datatype: 'Double'  },
  { name: `${LINE}.CNC-01.data.spindle_speed`, datatype: 'Int32'   },
  { name: `${LINE}.TEST-01.data.result_ok`,    datatype: 'Boolean' },
];
```

debug 사이드바에 키별로 값이 일일이 챍힅니다 (값이 변할 때마다 1 msg).

### 5) 주의

- 도커 쪽 flow 가 먼저 Deploy 되어 있어야 함 (addVariable 명령이 돌아 변수들이 등록된 이후에 구독 가능)
- `localhost` 로 안 붙으면 `127.0.0.1` 로 바꿔서 다시 빌드 (IPv6 파싱 이슈)
- 호스트 Node-RED 의 1883 은 MQTT 기본 포트와 같은 숫자이지만 여기서는 admin UI 용일 뿐 충돌 아님

## flow 검증 / 단위 테스트

빌드 직후 자동 회귀 테스트:

```bash
cd equip-sim
LINE_ID=LINE-01 python nodered/build_flow_das.py --host-mode docker
python nodered/_verify_flow_das.py        # wires/ref/JS syntax 정적 검증
node nodered/_test_emit_payload.js        # status / quality / cycle_time 판정
node nodered/_test_publish.js             # NodeId 매핑 / dtype 자동 판정
```

기대 결과: `ALL OK` / `ALL ASSERTIONS PASSED`.

---

## 트러블슈팅

- **mcprotocol 노드가 빨강** — Node-RED 컨테이너를 다시 빌드하세요.
  (`Dockerfile.nodered` 의 `util.log` 패치가 적용되지 않은 베이스로 띄운 경우)
- **OPC UA Server 가 4870 으로 안 열림** — Node-RED 컨테이너가 4870/4970/5070 중
  맞는 포트를 publish 했는지, flow 안의 `OpcUa-Server` 노드가 deploy 되었는지 확인.
- **`equipment_name` 이 `${LINE_ID:-...}` 그대로 노출** — `sim/config.py` 가
  치환 안 한 상태. `python -c "from sim.config import load_config; print(load_config('configs/line1/CAST-01.json').equipment_name)"` 로 확인.
- **포트 충돌** — 같은 라인을 두 번 띄웠을 수 있습니다.
  `docker compose --env-file .env.line01 ps` 로 점검 후 down.
- **flow 가 옛날 포트로 접속 시도** — 라인을 바꿨을 때 flow 를 다시 빌드 안 함.
  `LINE_ID=LINE-0X python nodered/build_flow_das.py --host-mode docker` 재실행 후
  Node-RED 에서 다시 import.
