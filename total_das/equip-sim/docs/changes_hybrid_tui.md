# Hybrid TUI + 파일 로그 (이번 라운드)

> 목표: `attach` 는 제어반 TUI, `logs` 는 평문 로그. 사용자 요청 그대로 두 채널을 깨끗하게 분리한다. `up-all.ps1` 에서 모두 다룬다.

## 1. 왜 하이브리드인가

Docker 는 PID1 의 stdout 단일 스트림이 `docker logs` 와 `docker attach` 양쪽으로 동시에 흐른다. 둘을 다른 모양으로 분리할 수가 없다. 그래서:

| 채널 | 보이는 것 | 어떻게 |
|---|---|---|
| `docker attach <c>` | 제어반 TUI (Textual) | PID1 이 TUI 를 화면에 그림 |
| `docker logs <c>` | (TUI 모드에선 거의 비어있음) | stdout 핸들러 OFF |
| `docker exec <c> tail -f /var/log/equip-sim.log` | 평문 로그 | FileHandler 가 항상 켜짐 |

PID1 은 TUI 만 그리고, 로그는 파일에 따로 적어둔 뒤 `up-all.ps1 logs` 가 그 파일을 `tail -f` 한다.

## 2. 변경 파일

### `sim/log.py` (재작성)
- `configure_logging(mode)` — root logger 한 번에 설정.
- `FileHandler` 는 항상 추가 (`/var/log/equip-sim.log`, `SIM_LOG_FILE` env 로 변경).
- `mode='console'` 일 때만 `StreamHandler(stdout)` 도 같이 추가.
- 권한 문제로 파일 핸들러가 실패하면 자동으로 stdout 폴백 (안정성).
- 각 모듈은 `get_logger(__name__)` 로 child logger 만 받고 root 로 propagate.

### `main.py`
- 시작 직후 `mode` 를 먼저 결정 → `configure_logging(mode=mode)` 한 번 호출 → cfg 로드.
- mode 결정 순서: `--tui/--console` CLI 인자 → `SIM_MODE` env → `sys.stdout.isatty()` (TTY 면 tui).
- TUI 모드: protocol runner 를 백그라운드 스레드로, Textual app 은 메인 스레드.

### `Dockerfile`
- `RUN mkdir -p /var/log && chmod 777 /var/log` 추가.
- 컨테이너 사용자가 root 가 아니어도 FileHandler 가 쓰기 가능.

### `docker-compose.yml`
- 9 개 설비 service 의 `environment:` 에 `SIM_MODE: ${SIM_MODE:-tui}` + `SIM_LOG_FILE: /var/log/equip-sim.log` 일괄 추가.
- YAML merge 규칙상 anchor 의 environment 가 service 쪽에 의해 통째로 가려지기 때문에 anchor 가 아닌 각 service 에 직접 박았다.
- `tty: true` + `stdin_open: true` 는 anchor 에 유지.
- 기본 동작: `docker compose up -d` → 모든 설비가 TUI 모드로 부팅 → attach 가능.
- 굳이 stdout 로그를 보고 싶으면: `$env:SIM_MODE = "console"; .\scripts\up-all.ps1 up`.

### `scripts/up-all.ps1` (대대적으로 확장)
새 subcommand 4 개 추가:

```powershell
.\scripts\up-all.ps1 attach LINE-01/CAST-01     # 제어반 TUI 진입 (detach: Ctrl-P Ctrl-Q)
.\scripts\up-all.ps1 attach CAST-01             # LINE-01 가정
.\scripts\up-all.ps1 logs   LINE-01/CAST-01     # 설비 한 대 평문 로그 follow
.\scripts\up-all.ps1 logs   LINE-02             # 라인 전체 compose logs (기존)
.\scripts\up-all.ps1 logs   INTEGRATION         # 통합 DAS (기존)
.\scripts\up-all.ps1 exec   LINE-01/CAST-01     # 컨테이너 안 sh 진입
.\scripts\up-all.ps1 exec   CAST-01 -ExecCmd "ls /var/log"
.\scripts\up-all.ps1 ps                         # 9×3 설비 상태 + 모드 한눈에
```

세부:
- `Normalize-Line` / `Normalize-Equipment` / `Resolve-Container` 로 입력 정규화. `LINE-01/CAST-01` / `LINE-01_CAST-01` / `CAST-01` 모두 같은 컨테이너로 매핑.
- `attach` 는 `--detach-keys "ctrl-p,ctrl-q"` 명시 → 실수로 Ctrl-C 안 누르도록 경고도 출력.
- `logs <설비>` 는 `docker exec <c> sh -c 'touch ...; tail -n 200 -f /var/log/equip-sim.log'` — 컨테이너가 막 떠서 파일이 없으면 touch 로 보장 후 tail.
- `ps` 는 9 × 3 격자에 `state` / `SIM_MODE` 컬럼까지 표시. 결손 컨테이너는 회색.

## 3. 사용 흐름

```powershell
# 1. 다 띄움 (전부 TUI 모드)
.\scripts\up-all.ps1 up

# 2. 상태 확인
.\scripts\up-all.ps1 ps

# 3. CAST-01 제어반 보기
.\scripts\up-all.ps1 attach LINE-01/CAST-01
#   ─ 사진 같은 power LED / sp / actual / 편집중 UI
#   ─ Tab 키로 Panel <-> Console 전환
#   ─ Ctrl-P Ctrl-Q 로 빠져나오기 (컨테이너 계속 돌아감)

# 4. 평문 로그가 보고싶을 때
.\scripts\up-all.ps1 logs LINE-01/CAST-01
#   ─ /var/log/equip-sim.log 의 tail -f

# 5. 컨테이너 내부 확인
.\scripts\up-all.ps1 exec LINE-01/CAST-01
.\scripts\up-all.ps1 exec CAST-01 -ExecCmd "cat /var/log/equip-sim.log | tail -50"
```

## 4. 주의 / 한계

- `docker attach` 의 Ctrl-C 는 PID1 에 SIGINT 가 전달되어 컨테이너가 정상 종료된다. 의도가 아니면 반드시 Ctrl-P Ctrl-Q 로 detach.
- TUI 모드에서 `docker logs <c>` 는 거의 비어 보임 (의도된 동작). 평문 로그는 `up-all.ps1 logs <설비>` 로.
- 컨테이너 재시작 시 `/var/log/equip-sim.log` 는 초기화된다. 호스트 mount 가 필요하면 service 에 `volumes: ["./logs/${LINE_ID}_<EQ>:/var/log"]` 를 직접 추가하면 됨 (이번 라운드에서는 추가 안 함 — 디스크 사용량 증가를 피하기 위해 기본은 컨테이너 내부).
