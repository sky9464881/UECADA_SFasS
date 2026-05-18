# 변경 내역 — 호스트 TUI 제어반 분리

## 핵심 결정

이전 시도(시뮬 컨테이너 내부 Textual TUI + `docker attach`)는 다음 한계로 폐기:

- `docker attach` 가 raw TTY 모드를 충분히 제공하지 못해 키 입력이 먹통
- SIGWINCH 가 컨테이너로 전달되지 않아 터미널 크기 변경 시 고정 크기
- ANSI escape 가 `docker logs` 출력과 섞여 깨짐

→ **시뮬레이터는 TUI 없던 헤드리스 버전으로 원복**, 호스트(로컬)에서 돌리는
**별도 TUI 패키지 `host_tui/`** 를 신설했습니다. 시뮬이 띄운 OPC UA / Modbus /
MC 서버에 호스트 클라이언트로 접속해 실시간 관제 + 라인/설비별 개별 창.

## 시뮬레이터 원복 (TUI 작업 전 상태로)

| 파일 | 변경 |
| --- | --- |
| `sim/log.py` | 파일 핸들러/`configure_logging` 제거. `get_logger` 만 남기고 stdout 한 줄 포맷. |
| `main.py` | argparse / `SIM_MODE` 분기 / TUI 진입점 제거. 헤드리스 단일 경로. |
| `sim/tui.py` | **삭제** |
| `requirements.txt` | `textual` 제거. (`asyncua`, `pymodbus`, `pyserial` 만) |
| `Dockerfile` | `RUN mkdir -p /var/log` 제거. |
| `docker-compose.yml` | `SIM_MODE`, `SIM_LOG_FILE`, `tty: true`, `stdin_open: true` 모두 제거. |
| `scripts/up-all.ps1` | `attach` / `exec` subcommand 제거. `logs` 는 `docker compose logs` 라인 단위로 복원. |

## 신규: `host_tui/` 패키지

```
host_tui/
├── __init__.py
├── __main__.py        # 진입점: python -m host_tui LINE-01/CAST-01
├── topology.py        # 라인/설비 → 호스트 포트 매핑
├── config.py          # configs/lineN/EQ.json 로더
├── clients.py         # OpcUaClient / ModbusClient / McClient + make_client
├── ui.py              # Rich Live + 입력 스레드 + 폴링 스레드
└── requirements.txt   # rich, asyncua, pymodbus, pymcprotocol
```

### 설계 원칙

- **단순/안정 우선**: `prompt_toolkit` 같은 통합 안 씀. `Rich Live(screen=True)` + 별도 입력 스레드.
- **이벤트 기반 redraw**: 폴링 스레드가 1초마다 값 갱신 → `state.mark_dirty()` → 메인 루프가 즉시 또는 최대 1초 대기 후 redraw.
- **설비당 인스턴스 하나**: `python -m host_tui LINE-01/CAST-01` 처럼 라인/설비 단위로 실행. 다중 설비는 PowerShell 윈도우 여러 개로 열기.

### 사용법

```powershell
# 시뮬 띄우기 (변경 없음)
.\scripts\up-all.ps1 up

# 호스트 측에서 별도 PowerShell 창
pip install -r host_tui\requirements.txt
python -m host_tui LINE-01/CAST-01
python -m host_tui LINE-02_CNC-01
python -m host_tui CAST-01            # LINE-01 가정
python -m host_tui --list             # 가용 라인/설비 목록
python -m host_tui LINE-01/CAST-01 --host 192.168.0.10  # 원격
```

### UI 디자인 (사진 기반)

- 회색 패널 배경 (`grey50`), 밝은 회색 라벨 박스(`grey78`, 검정 글씨)
- 검정 배경 7-seg 스타일 값 박스
  - **SP 값**: 빨강(`red1`)
  - **Actual 값**: 흰색(`white`)
  - **편집 중**: 노랑(`yellow1`) + 옆에 회색 ○ 확인 버튼
- **Power LED**: ON=초록(`green3`), OFF=빨강(`red3`)
- **포커스**: 노란 border (`yellow1`)
- 라벨/값 모두 가운데 정렬

### 조작

| 키 | 동작 |
| --- | --- |
| ← ↑ → ↓ | 포커스 이동 (power → sp1 → sp2 → ...) |
| Enter | power: 토글 / sp: 편집 진입 → 다시 Enter 로 commit |
| Esc | 편집 취소 |
| 숫자 / `-` / `.` | 편집 중 버퍼에 추가 |
| Backspace | 편집 중 한 글자 지움 |
| Q / Ctrl-C | 종료 |

### 프로토콜 클라이언트

| Protocol | Backend | 비고 |
| --- | --- | --- |
| `opcua` | `asyncua.sync.Client` | NodeId = `ns=<auto>;s=<tag_name>` |
| `modbus` | `pymodbus.ModbusTcpClient` | slave_id=1, float = big-endian word order |
| `modbus-rtu-tcp` | `ModbusTcpClient` + `ModbusRtuFramer` | CNC 게이트웨이 |
| `mcprotocol` | `pymcprotocol.Type3E` | M=bit, D word, float little-endian (`<HH`/`<f`) |

태그 매핑은 `configs/lineN/EQ.json` 의 `tag.mc` / `tag.mb` / OPC UA name 을 그대로 재사용. `${LINE_ID:-LINE-00}` 표기는 호스트 측에서 `topology.resolve()` 가 결정한 `line_id` 로 전개.

### 폴링 / 입력 스레드

- **poll_thread**: 1초마다 모든 태그 `client.read()` → `state.values` 갱신 → `mark_dirty`. `power` 가 None 이면 연결끊김으로 표시.
- **input_thread (Windows)**: `msvcrt.kbhit/getch`, 화살표 = `0xe0` prefix.
- **input_thread (POSIX)**: `termios.setcbreak` + `select` 로 ESC 시퀀스 처리.

## 보존된 파일

`sim/state.py`, `sim/config.py`, `sim/protocols/*` (mc_server, modbus_server, opcua_server, mc_protocol_codec) 와 `configs/lineN/*.json` 은 모두 변경 없음. Node-RED 플로우(`flows_*.json`) 도 그대로 호환.

---

# 추가 작업 — 컨테이너 내부 TUI Wrapper (`sim/tui_wrapper/`)

## 배경

`host_tui/` 는 외부에서 OPC UA/Modbus/MC 로 접속하므로 "실제 산업 프로토콜을 그대로 타고 들어가는" UI 입니다. 반면 **"외부 프로토콜을 거치지 않고 시뮬의 내부 변수를 직접 들여다보고 싶다"** 는 요구가 별도로 있어, 컨테이너 내부 전용 TUI 를 추가했습니다.

## attach vs exec 분석

| 항목 | `docker attach` | `docker exec -it` |
| --- | --- | --- |
| TTY | PID 1 stdin/stdout 공유 → raw mode 협상 불가, logs 와 섞임 | 새 프로세스에 PTY 별도 할당 |
| SIGWINCH | 전달 안 됨 | 정상 전달 |
| 멀티 클라이언트 | 불가 (PID 1 stdin 1개) | 가능 (각자 PTY) |
| Ctrl-C 영향 | **시뮬 본체가 죽음** | exec 세션만 종료 |

결론: **`exec -it` 채택**. 다만 같은 컨테이너 안의 별도 프로세스라 `state` 객체를 직접 못 잡으므로 **IPC 필요**.

## IPC: Unix Domain Socket + JSON line

- 경로: `/tmp/sim-tui.sock` (컨테이너 내부 한정, 외부 노출 절대 금지)
- 권한: 0600
- 서버: `ThreadingMixIn + UnixStreamServer` (멀티 클라이언트)
- 메시지: `OP_PING / OP_READ / OP_WRITE`, 응답 `STATUS_OK / STATUS_ERR`
- 모든 write 는 **반드시 `state.set_external()` 경유** → RO 검사·coerce·로깅 일관성 유지
- daemon thread 로 백그라운드 동작. **시작 실패해도 시뮬은 그대로 진행** (로그 경고만)

## 파일 구성

```
sim/tui_wrapper/
├── __init__.py        # 패키지 docstring
├── __main__.py        # python -m sim.tui_wrapper 진입점 (argparse)
├── protocol.py        # 메시지 스키마, TagInfo, recv_line / send_msg
├── server.py          # PID 1 측 UDS 서버. start_in_background(state)
├── client.py          # exec 측 동기 RPC: ping / read / write
└── application.py     # Rich Live TUI 본체 (host_tui/ui.py 와 동일 팔레트)
```

## 시뮬 본체 통합 (단 한 줄)

`main.py`:

```python
from sim.tui_wrapper.server import start_in_background as start_tui_ipc
...
state = EquipmentState(cfg)
stop_event = threading.Event()
start_tui_ipc(state)   # ← 추가
```

## 사용법

```powershell
# 시뮬이 떠 있는 상태에서
docker exec -it LINE-01_CAST-01 python -m sim.tui_wrapper
# 또는 소켓 경로 지정
docker exec -it LINE-01_CAST-01 python -m sim.tui_wrapper --socket /tmp/sim-tui.sock
```

조작: ←↑→↓ 또는 **WASD / hjkl** 이동, Enter 토글/편집/commit, Esc 취소, 숫자/-/. 입력, BS 지움, Q/Ctrl-C 종료.

### 입력 안정성 메모

Windows Terminal → ConPTY → `docker exec` PTY 경로는 ANSI escape 시퀀스가 솨개져 도착하고 지연이 클 수 있어 `\x1b[A` (화살표) 가 ESC 단독으로 오인되는 장애가 있었습니다. 해결:

- `os.read` + 내부 버퍼로 재조립, ESC 시퀀스 완성 대기 200ms 까지 허용
- CSI 종료 문자(0x40~0x7E) 기준 파싱으로 `\x1b[1;5A` 같은 변종도 화살표로 제대로 인식
- WASD / hjkl 대체 입력 원래 지원 (escape 시퀀스 의존 제로)

## `host_tui/` 와의 차이

| | `host_tui/` (호스트) | `sim/tui_wrapper/` (컨테이너 내부) |
| --- | --- | --- |
| 경로 | 호스트 → 산업 프로토콜 → 시뮬 | exec 프로세스 → UDS → 시뮬 |
| 보는 값 | 프로토콜 통해 본 "외부 관점" | `EquipmentState` 의 "날것" |
| 권한 | 산업 프로토콜의 ACL/RO 규칙 적용 | `set_external` RO 마스크는 동일하게 적용 |
| 노출 | 호스트 포트 매핑 필요 | 컨테이너 내부 한정 (보안) |

같은 UX 를 두 채널에서 유지하려고 코드 일부 중복(렌더링/입력) 은 의도적으로 둡니다.

## 의존성

- `requirements.txt` 에 `rich==13.7.1` 추가 (시뮬 컨테이너 이미지에 포함).
- Dockerfile / docker-compose.yml 변경 없음. UDS 가 컨테이너 내부 `/tmp/` 라 마운트도 필요 없음.
