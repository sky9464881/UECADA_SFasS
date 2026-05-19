# equip-sim — 신입 개발자 가이드

처음 합류한 분을 위한 **하루짜리** 길잡이입니다.
세부 명세는 [`README.md`](./README.md), 변경 이력은 [`CHANGES.md`](./CHANGES.md) 를 보세요.
이 문서는 "이 레포가 뭘 하는 물건이고, 어디부터 손대면 되나" 를 빠르게 잡아주는 게 목표입니다.

---

## 1. 이 레포가 뭐 하는 거예요?

한 줄 요약 — **공장 설비를 흉내내는 시뮬레이터** + **그 위의 라인 DAS** + **호스트에서 띄우는 제어반 TUI** 묶음.

실제 공장에서는 PLC/HMI/SCADA 가 산업 프로토콜(MC Protocol, Modbus, OPC UA)로 설비와 통신하는데, 우리는:

1. **시뮬레이터**(`sim/`) — 설비 9종을 도커 컨테이너 9개로 띄워서 위 3가지 프로토콜 서버를 흉내냅니다.
2. **라인 DAS**(`nodered/`) — Node-RED 가 1초마다 설비를 폴링해 라인 단위 JSON 페이로드를 만들고, OPC UA Server 로 재발행합니다.
3. **통합 DAS**(`integration/`) — 라인 3개의 DAS 를 한 곳에 모아 평면 NodeId 로 재노출합니다.
4. **호스트 TUI**(`host_tui/`) — 노트북 터미널에서 "라인-01 의 CAST-01" 같은 설비 하나를 직접 잡아서 제어반처럼 보고/조작하는 UI.

```
   ┌────────────── 도커 컨테이너 ──────────────┐    ┌── 호스트 ──┐
   │  설비 시뮬 9개  →  Node-RED 라인 DAS  →   │ →  통합 DAS    │
   │  (MC/Modbus/OPC UA 서버)    OPC UA 4860   │    OPC UA 5860 │
   │                                           │                │
   │                                           │  host_tui      │
   │  ←──────── 같은 OPC UA/Modbus/MC ─────────│  (Rich TUI)    │
   └───────────────────────────────────────────┘  └─────────────┘
```

설계 철학(잊지 마세요):
- **과설계 금지.** 클래스 계층 깊이 파지 말 것.
- 웹 UI/DB/인증/REST API 없음. 로그는 stdout.
- 종료는 SIGINT/SIGTERM 으로 graceful.
- 설명보다 **돌아가는 파일** 우선.

---

## 2. 디렉토리 한 번에 보기

```
equip-sim/
├── main.py                 시뮬레이터 PID 1. 컨테이너 안에서 이게 돌아요.
├── sim/                    시뮬레이터 본체 (헤드리스)
│   ├── config.py           configs/*.json 로더
│   ├── state.py            태그 값 보관 + 1초 tick 업데이트
│   ├── log.py              stdout 로거
│   └── protocols/
│       ├── mc_server.py        CAST-01 → MC Protocol 3E Binary
│       ├── modbus_server.py    WASH-01(TCP), CNC-01/02/03(RTU-over-TCP)
│       └── opcua_server.py     ASSY-01/02, TEST-01/02 → OPC UA
│
├── configs/
│   ├── _generate.py        line1/2/3 × 9설비 = 27개 json 재생성기
│   ├── line1/*.json        설비별 태그 정의 + 프로토콜 매핑
│   ├── line2/*.json
│   └── line3/*.json
│
├── docker-compose.yml      라인 1개 = 9 설비 + Node-RED 1개
├── Dockerfile              시뮬레이터 이미지
├── Dockerfile.nodered      라인 DAS Node-RED 이미지
├── requirements.txt        시뮬 의존성 (asyncua, pymodbus, pyserial)
│
├── nodered/                라인 DAS Node-RED flow 빌더
│   ├── build_flow_das.py       LINE-0X 용 DAS flow 생성
│   ├── build_flow_host_viewer.py  호스트 측에서 라인 DAS 들 구독해서 보는 flow
│   └── flows_das_LINE-0X.json     빌드 결과
│
├── integration/            통합 DAS (라인 3개 머지)
│   ├── build_flow_integration.py
│   ├── docker-compose.yml
│   └── flows_integration.json
│
├── host_tui/               ★ 호스트에서 돌리는 제어반 TUI (Rich)
│   ├── __main__.py             python -m host_tui LINE-01/CAST-01
│   ├── topology.py             라인/설비 → 호스트 포트 매핑
│   ├── config.py               configs/*.json 호스트 측 로더
│   ├── clients.py              OPC UA/Modbus/MC 동기 클라이언트
│   ├── ui.py                   Rich Live + 입력 스레드 + 폴링 스레드
│   └── requirements.txt        rich, asyncua, pymodbus, pymcprotocol
│
├── scripts/
│   └── up-all.ps1          전체 (3 라인 + 통합 DAS) 일괄 기동/종료/로그
│
└── docs/
    └── integration_spec.md 라인 DAS ↔ 통합 DAS 페이로드 규격
```

---

## 3. "내 PC 에서 일단 띄워보자"

### 0) 사전 준비
- Docker Desktop (Windows/Mac) 또는 docker engine + compose v2 (Linux)
- Python 3.11+
- 윈도우라면 PowerShell

### 1) 한 방에 전체 기동

```powershell
.\scripts\up-all.ps1            # 3 라인 + 통합 DAS 일괄 기동
.\scripts\up-all.ps1 ps         # 상태 확인
.\scripts\up-all.ps1 logs -Line LINE-01
.\scripts\up-all.ps1 down       # 일괄 종료
```

기동이 끝나면 이 포트들이 열립니다 — 브라우저에서 확인해 보세요:

| 항목 | 주소 |
|---|---|
| LINE-01 Node-RED UI | http://localhost:2880 |
| LINE-02 Node-RED UI | http://localhost:3880 |
| LINE-03 Node-RED UI | http://localhost:4880 |
| 통합 DAS Node-RED UI | http://localhost:5880 |
| 통합 DAS OPC UA | `opc.tcp://localhost:5860/integration-das` |

### 2) 호스트 TUI 띄워서 직접 조작

다른 PowerShell 창 하나 더 열고:

```powershell
pip install -r host_tui\requirements.txt
python -m host_tui --list                  # 가용 라인/설비 목록
python -m host_tui LINE-01/CAST-01         # 주조기 제어반
python -m host_tui LINE-02/CNC-01          # 가공기
```

화면이 뜨면:
- **← ↑ → ↓** : 항목 이동 (power → sp1 → sp2 → ...)
- **Enter** : power 토글 또는 sp 편집 진입 → 다시 Enter 로 commit
- **숫자 / `.` / `-`** : 편집 중 입력
- **Backspace** : 한 글자 지움
- **Esc** : 편집 취소
- **Q / Ctrl-C** : 종료

값을 바꾸면 그 변화가 Node-RED debug 사이드바에서 1초 안에 보입니다. 신입 첫날 검증 루프로 이거 추천드려요.

---

## 4. 시뮬레이터 한 컨테이너 안에서 무슨 일이?

```
docker compose --env-file .env.line01 up cast-01
  ↓
컨테이너 PID 1 = python main.py
  ↓
sim.config.load_config("configs/line1/CAST-01.json")
  ↓
sim.state.EquipmentState(cfg)        # 태그 27개의 현재값 dict
  ↓
RUNNERS[cfg.protocol](cfg, state, stop_event)
  │
  ├─ "mcprotocol"      → sim.protocols.mc_server.run()
  ├─ "modbus"          → sim.protocols.modbus_server.run()
  ├─ "modbus-rtu-tcp"  → sim.protocols.modbus_server.run()
  └─ "opcua"           → sim.protocols.opcua_server.run()
```

각 프로토콜 서버는 두 가지를 동시에 합니다:

1. **1초 tick** — `state.tick()` 로 sensor/progress 값을 살짝씩 흔들기.
2. **요청 처리** — 외부 클라이언트(Node-RED, host_tui, UA Expert 등) 가 read/write 하면 `state` 의 값 읽거나 `state.set_external()` 로 쓰기.

핵심은 **`sim/state.py` 가 단 하나의 사실의 원천 (single source of truth)** 이라는 점이에요. 프로토콜 서버는 그저 얇은 어댑터입니다.

---

## 5. 설비 추가/변경하려면? — 손대는 순서

가장 흔한 작업이 "태그 하나 추가" 또는 "범위 조정" 일 거예요. 순서는 이렇습니다:

1. **`configs/_generate.py`** 수정 → 모든 라인에 같은 변경이 가야 하므로 여기서 한 번에.
2. **`python configs/_generate.py`** 실행 → `line1/2/3/*.json` 27개 재생성.
3. **`README.md` 의 태그 명세 표** 갱신.
4. **`nodered/build_flow_das.py`** 에 새 태그가 페이로드에 포함되도록 추가 (있다면).
5. **flow 재빌드 → 컨테이너 재기동** :
   ```powershell
   python nodered\build_flow_das.py    # 3 라인 flow 재빌드
   .\scripts\up-all.ps1 down
   .\scripts\up-all.ps1                # 새 이미지로 띄우기
   ```
6. **검증** — host_tui 띄워서 새 태그 값이 보이는지, Node-RED debug 에서 페이로드에 포함되는지 확인.

### 새 프로토콜 추가가 필요하면?

`sim/protocols/<new>_server.py` 하나 만들고 `main.py` 의 `RUNNERS` dict 에 등록하세요. 시그니처는 `run(cfg, state, stop_event)`. 그게 전부입니다 — 베이스 클래스 따로 없어요(과설계 금지).

호스트 TUI 쪽에서도 보려면 `host_tui/clients.py` 의 `make_client()` 분기에 클라이언트 추가.

---

## 6. host_tui 가 어떻게 동작하는지

신입이 가장 자주 만질 부분이라 좀 더 자세히 적어둡니다.

```
python -m host_tui LINE-01/CAST-01
   │
   ├─ topology.resolve("LINE-01/CAST-01")
   │     → Target(line_id, equipment, host, port=5001, line_dir="line1")
   │
   ├─ config.load_host_cfg("configs/line1/CAST-01.json", "LINE-01")
   │     → HostCfg(equipment_name, protocol, tags=[HostTag(...)])
   │
   ├─ clients.make_client(target, cfg)
   │     → OpcUaClient / ModbusClient / McClient (전부 동기 인터페이스)
   │     → .read(tag) / .write(tag, value) / .close()
   │
   └─ ui.run(target, cfg)
         ├─ poll_thread:  매 1초 모든 tag read → state.values 갱신 → mark_dirty
         ├─ input_thread: msvcrt(Windows) / termios(POSIX) 로 raw 키 읽기
         └─ main loop:    Rich Live(screen=True) — dirty 이벤트 또는 1초 경과 시 redraw
```

### 왜 prompt_toolkit 안 썼나
처음엔 컨테이너 안에 Textual TUI 를 넣고 `docker attach` 로 붙는 방식을 시도했어요. 근데:
- `docker attach` 가 raw TTY 를 충분히 제공 못해서 키 입력 먹통
- SIGWINCH 가 컨테이너로 안 와서 창 크기 변경 안 됨
- ANSI escape 가 `docker logs` 로도 흘러서 출력 깨짐

→ 시뮬은 헤드리스로 되돌리고, TUI 는 **호스트에서 별도 프로세스**로 분리. Rich + 단순 입력 스레드가 가장 안정적이었습니다. 이 결정은 절대 되돌리지 마세요.

### UI 디자인 메모
- 회색 패널 배경(`grey50`), 밝은 회색 라벨 박스(`grey78`), 검정 배경 7-seg 값 박스
- **SP=빨강**, **Actual=흰색**, **편집중=노랑** + 옆에 회색 ○ 확인 버튼
- **Power LED**: ON=초록, OFF=빨강
- 포커스된 패널은 노란 border

색상 상수는 `host_tui/ui.py` 최상단에 있습니다. 디자인 바꾸려면 여기만 만지세요.

---

## 7. 자주 마주치는 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| `compose up failed` + `requirements.txt: not found` | 빌드 컨텍스트에 파일 없음. `Test-Path requirements.txt` 확인. `.dockerignore` 가 CRLF 로 변질됐는지도 확인 |
| host_tui 에서 값이 `----` 만 보임 | 시뮬 컨테이너가 안 떠 있거나 포트가 안 publish 됨. `docker compose ps` 로 확인 |
| OPC UA 클라이언트가 `localhost` 로 못 붙음 | IPv6(`::1`) 이슈. `--host 127.0.0.1` 로 |
| mcprotocol 노드가 빨강 | Node 24+ 에서 `util.log` 깨짐. `Dockerfile.nodered` 의 `sed` 패치 필수 |
| `equipment_name` 이 `${LINE_ID:-...}` 그대로 노출 | `LINE_ID` 환경변수가 컨테이너로 안 들어감. `.env.lineN` 확인 |
| flow 가 옛 포트로 접속 시도 | flow 재빌드 안 함. `python nodered/build_flow_das.py` 다시 |

더 많은 케이스는 [`README.md` § 트러블슈팅](./README.md) 참고.

---

## 8. 첫 주 추천 학습 경로

| Day | 할 일 |
|---|---|
| 1 | 본 문서 + `README.md` 읽기. `up-all.ps1` 로 전체 띄우고 Node-RED UI 4개 모두 들어가 보기. |
| 2 | `host_tui` 띄워서 sp 값 바꿔보기 → Node-RED debug 에서 변화 추적. `configs/line1/CAST-01.json` 열어서 태그 하나의 범위를 바꿔보고 컨테이너 재기동 → 변화 확인. |
| 3 | `sim/protocols/mc_server.py` 따라가기. 어떤 함수가 read 요청을 받아서 어떻게 `state` 에 닿는지 한 사이클 추적. |
| 4 | `nodered/build_flow_das.py` 열어서 페이로드 만드는 function 노드 코드 읽기. 새 디버그 노드 하나 추가해서 deploy 까지 해 보기. |
| 5 | 작은 PR — 예) host_tui 에 alarm 태그 빨강 깜빡임 표시, 또는 CAST-01 의 sp 범위 검증을 시뮬 쪽에 추가. |

---

## 9. 도움 청할 때

- 문서 우선순위: **이 문서 → `README.md` → `CHANGES.md` → `docs/integration_spec.md`**
- 코드 우선순위: **`main.py` → `sim/state.py` → `sim/protocols/*` → `host_tui/__main__.py` → `host_tui/ui.py`**
- 막히면 stdout 로그부터. `docker compose logs <서비스>` / `docker compose logs nodered`
- 그래도 안 풀리면 팀에 물어보세요. 그게 빠릅니다.

---

환영합니다. 행복한 시뮬레이팅 되세요.
