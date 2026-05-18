# 시뮬레이터 리팩토링 가이드

이 문서는 `sim/` 패키지(파이썬 헤드리스 설비 시뮬레이터)를 **유지보수 가능하고 재사용 가능한 모양**으로 다듬으려는 사람을 위한 길잡이입니다.

대상 독자
- 시뮬에 새 프로토콜(예: EtherNet/IP, S7) 또는 새 설비 유형을 추가해야 하는 사람
- 현재 `protocol-server`별로 흩어진 코드 중복을 정리하고 싶은 사람
- 시뮬을 다른 프로젝트(다른 공장, 다른 라인 토폴로지)에서 라이브러리처럼 가져다 쓰고 싶은 사람

읽기 전 권장:
- [`README.md`](./README.md) — 사용자 관점의 명세/포트표
- [`ONBOARDING.md`](./ONBOARDING.md) — 전체 그림
- [`CHANGES.md`](./CHANGES.md) — 그동안의 결정 이력

> 🚫 시작 전에 한 번 더 — **"과설계 금지"** 가 이 레포의 1원칙입니다. 본 가이드도 가능한 작은 리팩토링부터 큰 리팩토링 순으로 정렬해뒀어요. 위에서부터 차근차근 적용하고, 아래쪽 큰 변경은 정말 필요한 시점에만 손대세요.

---

## 1. 현재 아키텍처 한눈에

```
┌──────────────────────────────────────────────────────────────────────┐
│                          equip-sim (한 컨테이너 = 한 설비)               │
│                                                                      │
│  main.py                                                             │
│    └─ load_config(SIM_CONFIG=configs/lineN/<EQ>.json)                │
│         └─ SimConfig + [TagConfig]                                   │
│              │ ── role: power/setpoint/sensor/counter/alarm          │
│              │ ── data_type: bool/int/float                          │
│              │ ── mc / mb 매핑                                          │
│         └─ EquipmentState(cfg) ★ single source of truth              │
│              │ ── tick(): power=ON 일 때 sensor/counter/alarm 재계산      │
│              │ ── set_external(name, val): 외부 write 진입점              │
│              │ ── read(name) / read_all()                            │
│         └─ RUNNERS[cfg.protocol](cfg, state, stop_event)             │
│              ├─ sim.protocols.mc_server.run        (MC Protocol 3E) │
│              ├─ sim.protocols.modbus_server.run    (TCP/RTU/RTU-TCP) │
│              └─ sim.protocols.opcua_server.run     (asyncua)         │
└──────────────────────────────────────────────────────────────────────┘
```

핵심 불변성:
1. **`EquipmentState` 가 유일한 진실의 원천**. 프로토콜 서버는 얇은 어댑터.
2. **`power == False` 면 sensor/counter/alarm 강제 0/false**. 이 규칙은 `state.tick()` 안에 박혀 있어요.
3. 외부 write 는 **무조건** `state.set_external()` 한 곳으로 모입니다. 그래야 RO 검사·coerce·로깅이 일관됩니다.
4. 종료는 `stop_event: threading.Event`. 모든 runner 가 이걸 polling 으로 본다.

---

## 2. 사용 중인 외부 라이브러리

리팩토링하다 보면 "이걸 왜 이렇게 썼지?" 가 자주 나옵니다. 핵심 결정 이유를 같이 적어둡니다.

### 2.1 `pymodbus==3.6.6` — Modbus TCP / RTU / RTU-over-TCP

#### 왜 이 버전 (3.6.6)?
- 3.7+ 부터 API 가 또 한 번 바뀌었습니다 (`Framer` enum → `FramerType`, `StartAsync*` deprecation 등). 4.x 는 더 큰 변화 예고.
- 우리는 **`pymodbus.framer.Framer` enum + `StartAsyncTcpServer`/`StartAsyncSerialServer`** 조합을 쓰는 마지막 안정 라인이 3.6.x 라서 여기로 고정.
- 업그레이드 시 영향 받는 파일: `sim/protocols/modbus_server.py` 의 import + `Framer.RTU` 부분.

#### 어떻게 쓰는가
```python
from pymodbus.datastore import (
    ModbusSequentialDataBlock, ModbusServerContext, ModbusSlaveContext,
)
from pymodbus.framer import Framer
from pymodbus.server import StartAsyncTcpServer, StartAsyncSerialServer
```

- **datastore**: 슬레이브 1개당 `coil / discrete input / holding register / input register` 4개 블록.
  우리는 `ModbusSequentialDataBlock(0, [0] * size)` 로 0번지부터 N개 시퀀셜 할당.
- **`ModbusSlaveContext`**: 1개 슬레이브의 datastore 묶음. 우리는 이걸 상속한 `WriteAwareSlaveContext` 로 **`setValues()` 를 가로채** 외부 write 를 잡습니다.
- **`ModbusServerContext`**:
  - TCP 모드 → `slaves=slave, single=True` (slave_id 무시)
  - RTU/RTU-TCP → `slaves={cfg.slave_id: slave}, single=False`
- **Framer 의 의미**:
  - `Framer.SOCKET` (기본) — 표준 Modbus-TCP 프레임 (MBAP 헤더)
  - `Framer.RTU` — RTU 프레임(unit id + PDU + CRC16). 시리얼 또는 RTU-over-TCP 게이트웨이용.
  - `Framer.ASCII` — 거의 안 씀.

#### `WriteAwareSlaveContext` 패턴 (이 레포의 핵심)
pymodbus 는 클라이언트 write 콜백을 직접 노출하지 않습니다.
대신 `setValues()` 를 override 해서 **외부 write 와 내부 push 를 구분**합니다.

```python
class WriteAwareSlaveContext(ModbusSlaveContext):
    def _internal_set(self, fx, address, values):
        self._internal_write = True
        try: super().setValues(fx, address, values)
        finally: self._internal_write = False

    def setValues(self, fx, address, values):
        if not self._internal_write:
            # 클라이언트가 보낸 write → state.set_external() 로 전달
            ...
        super().setValues(fx, address, values)
```

> ⚠️ **빠지기 쉬운 함정**: 시뮬이 sensor 값을 datastore 로 밀어 넣을 때 그냥 `slave.setValues()` 를 부르면 자기 자신이 보낸 write 를 외부 write 로 오인합니다. 그래서 우리는 **`_internal_set`** 을 통해 플래그 켜고 호출. 새 프로토콜에 추가할 때도 같은 패턴 유지.

#### RTU-over-TCP 가 왜 필요해요?
실제 공장에서 CNC, 오래된 PLC 는 RS-485/RS-232 시리얼만 지원하는데, 이를 이더넷에 올리는 **Moxa NPort** 같은 시리얼-이더넷 게이트웨이가 거의 표준입니다. 게이트웨이는 TCP 소켓에 RTU 프레임을 **그대로** 흘려보냅니다(MBAP 헤더 없음). pymodbus 의 `StartAsyncTcpServer(framer=Framer.RTU)` 가 이 동작을 그대로 흉내냅니다.

Node-RED 측 `node-red-contrib-modbus` 에서는 client type=`TCP` + tcpType=`RTU-BUFFERED` 로 붙입니다.

### 2.2 `pyserial==3.5` — RTU 시리얼 백엔드

pymodbus 가 내부적으로 씁니다. 우리 코드가 직접 `serial.Serial(...)` 호출하지는 않아요. `pyserial` 은 `pymodbus[serial]` 의존성으로 따라옵니다만, 컨테이너 빌드 안정성을 위해 명시적으로 pin.

> 현재 활성 라인업에서 `protocol == "modbus-rtu"` (순수 시리얼) 는 안 쓰고 `modbus-rtu-tcp` 만 사용 중. 그래도 코드 경로는 남아있어 향후 진짜 시리얼 디바이스 붙일 때 재활용 가능합니다.

### 2.3 `asyncua==1.1.5` — OPC UA 서버

#### 왜 1.1.5?
- `asyncua` 는 0.x → 1.x 에서 NodeId 생성 API, security 관련 함수, `set_writable()` 의 동작 등이 미묘하게 바뀌었습니다.
- 1.1.5 는 **`set_writable()` 가 AccessLevel + UserAccessLevel 둘 다 켜주는** 첫 안정 버전. 이전 버전은 한 쪽만 켜져서 일부 클라이언트(`node-red-contrib-opcua` 포함) 가 write reject.

#### 어떻게 쓰는가
```python
from asyncua import Server, ua
server = Server(); await server.init()
server.set_endpoint(f"opc.tcp://{host}:{port}/{equipment}/")
idx = await server.register_namespace(uri)
eq_obj = await server.nodes.objects.add_object(ua.NodeId(name, idx), name)
var = await eq_obj.add_variable(
    ua.NodeId(tag_name, idx), tag_name, initial,
    varianttype=ua.VariantType.Float,    # 또는 Int32 / Boolean
    datatype=ua.NodeId(ua.ObjectIds.Float),
)
if writable: await var.set_writable()
```

#### NodeId 명시의 중요성
`add_variable("BrowseName", value)` 처럼 인자를 단순화하면 asyncua 가 **랜덤 numeric NodeId** 를 부여합니다. 그러면 클라이언트가 `ns=2;s=<tag_name>` 을 못 찾아요. 그래서 우리는 **항상** `ua.NodeId(t.name, idx)` 로 string identifier 를 명시합니다. 이것 하나만 빠져도 통합 DAS 에서 변수 안 보이는 디버깅 1시간 코스를 탈 수 있습니다.

#### Write 감지: polling vs callback?
asyncua 는 SubscriptionHandler 로 write 콜백을 받을 수 있지만, 1.x 에서는 등록 방식이 두세 번 바뀌었고 락 처리가 살짝 까다롭습니다. 우리는 **"writable 변수만 1초에 한 번 read 해서 직전 값과 다르면 외부 write 로 간주"** 라는 **폴링 비교** 방식을 씁니다 (`WriteHandler.poll()`).

이 방식의 단점은 **같은 값으로 두 번 write 한 케이스는 못 잡는다**는 점인데, 우리 시뮬에서는 의미 없는 케이스라 OK. 만약 향후 진짜 콜백이 필요해지면 `SubHandler` 또는 `UserManager` 로 갈아끼우면 됩니다.

#### Variant 와 DataType 의 짝
`varianttype` 과 `datatype` 둘 다 지정해야 안전합니다. 둘이 안 맞으면 일부 클라이언트가 write 거절합니다. 우리는 둘 다 같이 결정하는 함수 두 개 (`_variant_type`, `_data_type_node`) 로 묶어 둠.

### 2.4 표준 라이브러리만 쓰는 부분

#### MC Protocol 서버
**`pymcprotocol`** 같은 클라이언트 라이브러리는 있지만, **서버 측 구현체는 사실상 없습니다**. 그래서 `sim/protocols/mc_server.py` 는 **`socketserver.ThreadingTCPServer` + `struct`** 로 처음부터 짰습니다.

핵심 라이브러리:
- `socketserver` — TCP 서버 boilerplate
- `socket.settimeout(60.0)` — Node-RED contrib 노드의 long-lived 연결에 맞춘 idle timeout
- `struct.pack/unpack("<HH", ...)`, `"<f"` — **little-endian 2-word float** (Mitsubishi PLC 의 일반적 word order)

지원 명령은 의도적으로 4개만:
| Cmd / SubCmd | 설명 |
|---|---|
| 0x0401 / 0x0000 | Batch Read Word |
| 0x0401 / 0x0001 | Batch Read Bit |
| 0x1401 / 0x0000 | Batch Write Word |
| 0x1401 / 0x0001 | Batch Write Bit |

Random Read 등은 안 씁니다. 필요해지면 `handle_*` 메서드 추가하면 됩니다.

#### 동시성
- **`threading` + `asyncio` 혼용**:
  - mc_server: 순수 thread (socketserver 가 thread-based)
  - modbus_server, opcua_server: `asyncio.run(_serve(...))` 안에 task 여러 개
- **공유 상태 보호**: `EquipmentState._lock` (threading.Lock). state 안에서만 lock 을 쥐고 외부에는 dict copy 만 노출.
- **graceful shutdown**: `stop_event = threading.Event()` 를 모든 runner 에 주입. asyncio 측은 `asyncio.to_thread(stop_event.wait)` 로 await 가능하게 변환.

---

## 3. config 스키마

`configs/lineN/<EQ>.json` 의 구조 (현재):

```json
{
  "equipment_name": "${LINE_ID:-LINE-00}_CAST-01",
  "protocol": "mcprotocol",
  "sampling_ms": 1000,
  "tags": [
    { "name": "power", "role": "power", "data_type": "bool",
      "base_value": true,
      "mc": { "device": "M", "address": 0 } },
    { "name": "injection_pressure_sp", "role": "setpoint", "data_type": "float",
      "base_value": 80.0,
      "mc": { "device": "D", "address": 0 } },
    { "name": "injection_pressure", "role": "sensor", "data_type": "float",
      "source_sp": "injection_pressure_sp", "stddev": 1.5,
      "mc": { "device": "D", "address": 100 } }
  ]
}
```

| 필드 | 타입 | 의미 |
|---|---|---|
| `equipment_name` | string | OPC UA Object 이름, MC 매핑 로그 prefix. `${VAR:-default}` 전개 가능 |
| `protocol` | `"modbus" / "modbus-rtu" / "modbus-rtu-tcp" / "opcua" / "mcprotocol"` | runner 선택 |
| `sampling_ms` | int | tick 주기 |
| `host` / `port` | string / int | TCP 서버 바인드 주소 (옵션. compose 에서 환경변수로 주입) |
| `slave_id` | int | RTU/RTU-TCP 일 때만 |
| `serial_path` | string | RTU 일 때만 |
| `tags[]` | array | 태그 목록 |

태그(`TagConfig`):

| 필드 | 의미 |
|---|---|
| `name` | 태그 이름. OPC UA NodeId 의 string identifier 가 됨 |
| `role` | `power / setpoint / sensor / counter / alarm` |
| `data_type` | `bool / int / float` |
| `base_value` | 초기값. counter 는 시작값, alarm 은 고정값 |
| `stddev` | sensor 노이즈 표준편차. 외부 노출 안 됨 |
| `source_sp` | sensor 의 중심값을 어느 setpoint 에서 가져올지 |
| `step` | counter 의 tick 당 증가량 |
| `mc.{device, address}` | MC Protocol 디바이스(M/D/X/Y/R/W) + 주소 |
| `mb.{kind, address}` | Modbus 매핑. `coil / hr_int / hr_float` |

### 검증 규칙 (`SimConfig.__post_init__`)
- `power` role 은 정확히 1개, `data_type=="bool"` 강제
- `counter` 는 `int` 만
- `sensor.source_sp` 는 실제 `setpoint` 태그를 가리켜야 함
- 프로토콜이 mcprotocol → 모든 태그에 `mc` 필수, 같은 `(device, address)` 중복 금지, float 는 +1 워드까지 점유 검사
- Modbus → 모든 태그에 `mb` 필수, kind 와 data_type 일치 검사, HR 공간 중복 검사

이 검증이 **시뮬 기동 즉시** 잘못된 config 를 잡아주는 마지막 방어선입니다. 새 검증 룰은 가능한 여기 추가하세요.

---

## 4. 리팩토링 우선순위 (작은 → 큰)

### 4.1 ★ 가장 먼저 — 공통 유틸 분리 (1~2시간)

현재 각 protocol 서버에 흩어진 공통 로직:

| 중복 코드 | 현재 위치 |
|---|---|
| `_log_mapping` 형식의 시작 로그 | modbus / mc / opcua 셋 다 비슷한 모양 |
| `state.read_all() → datastore push` 루프 | modbus, opcua (mc는 `tags_by_addr` loop) |
| 초기값을 datastore 로 sync 하는 코드 | modbus, mc, opcua 셋 다 |
| float coerce, int coerce, bool coerce | state.py, mc_server.py, opcua_server.py 셋 다 |

**제안**: `sim/protocols/_base.py` 신설:
```python
def log_mapping_header(protocol: str, equipment: str): ...
def coerce(t: TagConfig, raw: Any) -> Any: ...     # state._coerce 와 합칠 것
def sync_initial(state, tags, write_one_fn): ...   # 초기값 push 공통화
def updater_loop(state, tags, period, stop_event,
                 write_dynamic_fn): ...            # tick + push
```

이때 **베이스 클래스 만들지 마세요**. 함수 + 콜백 인자로 충분합니다.

### 4.2 RUNNERS 등록을 entry point 로 (30분)

현재 `main.py`:
```python
RUNNERS = {
    "modbus": modbus_server.run,
    "modbus-rtu": modbus_server.run,
    "modbus-rtu-tcp": modbus_server.run,
    "opcua": opcua_server.run,
    "mcprotocol": mc_server.run,
}
```

새 프로토콜 추가할 때마다 `main.py` 손대야 합니다. **간단한 데코레이터 레지스트리**로 분산:

```python
# sim/protocols/registry.py
RUNNERS: dict[str, Callable] = {}
def register(*names):
    def deco(fn):
        for n in names:
            RUNNERS[n] = fn
        return fn
    return deco

# sim/protocols/modbus_server.py
@register("modbus", "modbus-rtu", "modbus-rtu-tcp")
def run(cfg, state, stop_event): ...

# main.py
from sim.protocols import modbus_server, opcua_server, mc_server  # noqa: F401  (등록)
from sim.protocols.registry import RUNNERS
```

> 더 나아가 `importlib.entry_points` 까지 갈 필요는 없습니다. 우리는 모놀리식 단일 프로젝트.

### 4.3 `state._by_name` 접근 제거 (30분)

`mc_server.py` 끝부분에 `self.state._by_name[tname]` 직접 접근이 있습니다. 이는 캡슐화 위반. `EquipmentState.get_tag(name) -> TagConfig | None` 같은 공식 access 추가 후 교체.

### 4.4 logging 정리 (1시간)

현재 `sim/log.py` 는 stdout 만 쓰는 단순 로거지만, 라이브러리 로거(`pymodbus`, `asyncua`) 의 수준 조정이 흩어져 있습니다. 한 곳에서:

```python
# sim/log.py
def configure(level: str = "INFO"):
    logging.basicConfig(level=level, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    # 라이브러리 노이즈 조정
    logging.getLogger("asyncua").setLevel("WARNING")
    logging.getLogger("pymodbus").setLevel("WARNING")
```

`main.py` 시작 부분에 `configure(os.environ.get("LOG_LEVEL", "INFO"))` 한 줄.

### 4.5 `WriteAwareSlaveContext` 의 hr write 처리 추출 (1시간)

현재 `_handle_hr_write` 안에서 `hr_int / hr_float` 분기 + 값 변환을 인라인으로 합니다. 같은 변환이 `push_to_datastore` 의 반대 방향에도 있어요(`float_to_words`, `to_int16`).

**제안**: 태그 ↔ 워드 변환을 **codec** 으로 분리.
```python
# sim/protocols/mb_codec.py
def tag_to_words(t: TagConfig, value: Any) -> list[int]: ...
def words_to_tag(t: TagConfig, words: list[int]) -> Any: ...
```
mc 도 같은 패턴이 자연스럽게 따라옵니다 (`mc_codec.py` — 이미 일부 사실상 구현됨).

### 4.6 단위 테스트 (반나절)

지금 테스트가 없습니다. 다음 3개부터 추가하면 회귀를 거의 다 잡습니다:

1. **`tests/test_config.py`** — 잘못된 config 가 `__post_init__` 에서 raise 되는지 (모든 검증 룰 1개씩)
2. **`tests/test_state.py`** — power off → 모든 비-setpoint 가 0, sensor 가 source_sp ± stddev 범위 안, counter 증가, set_external coerce
3. **`tests/test_codec.py`** — float ↔ words 양방향 round-trip (modbus: big-endian, mc: little-endian)

프로토콜 서버 통합 테스트는 무거우니, 우선 위 3개의 **순수 로직 테스트** 만 적극 추가하세요.

### 4.7 ☆ 큰 리팩토링 — Plugin Architecture (필요할 때만)

여기까지 오는 동안 새 프로토콜 추가가 한 곳만 만지면 되는 모양이 됩니다. 그래도 부족하다면:

```
sim/
├── core/                  # 프로토콜 무관 로직
│   ├── config.py
│   ├── state.py
│   ├── codec.py           # bool/int/float coerce + word pack/unpack
│   └── log.py
├── protocols/
│   ├── _base.py           # register(), updater_loop, sync_initial
│   ├── modbus/
│   │   ├── server.py
│   │   ├── codec.py       # tag <-> words (big-endian)
│   │   └── slave.py       # WriteAwareSlaveContext
│   ├── mc/
│   │   ├── server.py
│   │   ├── codec.py       # tag <-> words (little-endian)
│   │   ├── image.py       # DeviceImage
│   │   └── frame.py       # 3E binary parse/build
│   └── opcua/
│       └── server.py
└── runner.py              # main() 의 내용물 (entry point 는 별도)
```

이 모양은 **프로토콜이 4개 이상**이 되거나, **각 프로토콜에 codec 변형(예: byte/word order 옵션) 이 추가**되기 시작할 때 의미가 있습니다. 지금은 3개라서 과합니다.

---

## 5. 새 프로토콜을 추가하는 절차 (체크리스트)

예시: Siemens **S7 (snap7)** 또는 **EtherNet/IP (pycomm3)** 를 추가한다고 가정.

### 5.1 config 스키마 확장
1. `sim/config.py` 에 `S7Mapping` dataclass 추가:
   ```python
   @dataclass
   class S7Mapping:
       area: str   # "DB" / "M" / "I" / "Q"
       db: int     # area=="DB" 일 때만
       address: int
   ```
2. `TagConfig` 에 `s7: Optional[S7Mapping] = None` 추가.
3. `SimConfig.__post_init__` 에 `protocol == "s7"` 분기로 매핑 필수/중복 검사 추가.

### 5.2 codec 작성
`sim/protocols/s7_codec.py`:
```python
def tag_to_bytes(t: TagConfig, value) -> bytes: ...
def bytes_to_tag(t: TagConfig, raw: bytes) -> Any: ...
```
S7 은 **big-endian**, DB 내부는 byte address + bit offset(bool) — Mitsubishi 와 정반대.

### 5.3 server 작성
`sim/protocols/s7_server.py`:
```python
@register("s7")
def run(cfg: SimConfig, state: EquipmentState, stop_event):
    # snap7 또는 직접 ISO-on-TCP (RFC 1006) 구현
    ...
```
- **핵심 규약 3가지**:
  1. `state.tick()` 을 sampling_ms 마다 1회
  2. 외부 write 가 들어오면 `state.set_external(name, value)` 호출
  3. `stop_event.is_set()` 시 깔끔 종료

### 5.4 main.py 손대지 않음
`@register("s7")` 데코레이터 + `from sim.protocols import s7_server  # noqa` 한 줄(있다면 `protocols/__init__.py` 에) 추가만으로 끝.

### 5.5 호스트 TUI 측 클라이언트도 추가 (선택)
`host_tui/clients.py` 의 `make_client()` 분기에 `S7Client` 추가. 호스트 TUI 로 직접 모니터링하려면 이게 있어야 합니다.

### 5.6 config 샘플 + 검증
- `configs/line1/NEW-EQ.json` 1개 작성
- `python -m sim.config configs/line1/NEW-EQ.json` (이런 CLI 진단 추가도 좋습니다)
- 컨테이너 띄워서 host_tui 로 read/write 확인

---

## 6. 모듈화 / 재사용 전략

### 6.1 sim 을 라이브러리처럼 쓰려면

현재 `sim.config`, `sim.state` 는 이미 **다른 프로젝트에서 그대로 import 가능**합니다. 의존성도 표준 라이브러리만 씁니다(`json`, `re`, `dataclasses`). 의존성 가벼우니 패키지 분리 욕심 내지 마세요.

```python
# 다른 프로젝트
from sim.config import load_config
from sim.state import EquipmentState

cfg = load_config("my_equipment.json")
state = EquipmentState(cfg)
state.tick()
print(state.read_all())
```

호스트 TUI 의 `host_tui/config.py` 가 좋은 사례 — sim 의 검증 로직과 별개로 **호스트 측 가벼운 view 객체**(HostTag, HostCfg)만 다시 정의해서 의존을 끊었습니다.

### 6.2 config 생성을 코드로

`configs/_generate.py` 가 라인1/2/3 × 9설비 = 27개 json 을 한 번에 만듭니다. 손으로 27개 동기화하지 마세요. **데이터(스펙)는 한 곳에만**.

스펙이 바뀌면:
1. `_generate.py` 수정
2. `python configs/_generate.py` 실행 → 27개 재생성
3. `git diff` 로 확인
4. 컨테이너 재기동

### 6.3 시뮬 ↔ 외부 인터페이스 4종

| 인터페이스 | 어디서 정의 | 안정성 |
|---|---|---|
| **config schema** | `sim/config.py` 의 dataclass | 깨면 모든 json 재생성 필요 |
| **`EquipmentState` API** | `set_external / read / read_all / tick` | host_tui, 모든 runner 가 의존 — 시그니처 절대 함부로 깨지 말 것 |
| **`runner(cfg, state, stop_event)` 시그니처** | 모든 protocol server | 새 프로토콜 모두 이 형태 따름 |
| **태그 페이로드 (JSON)** | Node-RED 라인 DAS 가 발행 | `docs/integration_spec.md` 따로 명세 |

리팩토링 시 위 4개의 경계만 안 깨면 내부는 자유롭게 바꿔도 됩니다.

### 6.4 테스트 가능성 향상

지금 코드는 **soft real-time** 이라 통합 테스트가 까다롭지만, 다음을 분리하면 테스트가 쉬워집니다:

- **Pure function**: codec, coerce, mapping 빌드 — 입출력만 있는 함수로
- **State machine**: `EquipmentState.tick()` — random.seed 고정 후 deterministic 검증
- **I/O 어댑터**: socket / asyncio — 통합 테스트는 별도, 작은 표면만

### 6.5 의존성 격리

```
sim/                ← 표준 라이브러리만 + (실행 시) asyncua, pymodbus
sim/protocols/      ← 프로토콜별 lib 의존 (modbus만 pymodbus 등)
host_tui/           ← 자기만의 requirements (rich, asyncua, pymodbus, pymcprotocol)
nodered/            ← Python (flow 빌드용). 런타임 의존성은 Node 측.
```

각 부분은 **자기 requirements.txt 안에서만** 의존성을 둡니다. 시뮬 컨테이너에 host_tui 의존성이 끼어들지 않게 유지.

---

## 7. 위험 신호 (하지 말 것)

오랜 결정의 결과들 — 다시 반복하기 쉬운 함정.

### 7.1 ❌ `set_external` 우회
외부 write 를 `state._values[name] = ...` 로 직접 박는 코드. 검증/coerce/로그가 다 깨집니다. 무조건 `set_external` 만 호출.

### 7.2 ❌ 베이스 클래스 계층
`AbstractProtocolServer` → `BaseModbusServer` → `ModbusTcpServer` 같은 OOP 트리. 본 레포의 1원칙(과설계 금지) 위반. **함수 + 데코레이터 등록** 으로 충분합니다. 4년 후 후임이 고마워합니다.

### 7.3 ❌ asyncio 모든 곳 도입
mc_server 는 thread 기반(socketserver) 으로 잘 돌고 있어요. 굳이 asyncio 로 통일하지 마세요. 통일성보다 **각 라이브러리에 맞는 모델** 이 더 단순.

### 7.4 ❌ Web UI / REST API / DB 추가
이 레포의 명시적 비목표. 관제용 UI 가 필요하면 `host_tui` 로, 영속화가 필요하면 통합 DAS Node-RED 에서 외부 DB 로.

### 7.5 ❌ 시뮬 안에서 status/quality/cycle_time 계산
이 셋은 Node-RED DAS 의 책임입니다. 시뮬은 **raw 센서값 + progress** 만 송출. 경계를 흐리지 마세요. (`docs/integration_spec.md` 참조)

### 7.6 ❌ pymodbus 4.x / asyncua 1.2+ 무지성 업그레이드
둘 다 minor 버전에서 호환 깨졌던 전적이 있습니다. 업그레이드 시 반드시:
- `_serve()` 의 server start 시그니처 확인
- WriteAwareSlaveContext 의 `setValues(fx, address, values)` 시그니처 확인
- asyncua 의 `set_writable()` 가 여전히 AccessLevel + UserAccessLevel 둘 다 켜는지 확인

---

## 8. 추천 작업 순서 (한 sprint 분량)

**Day 1**:
1. `sim/protocols/registry.py` 도입 + 데코레이터 등록 (§4.2)
2. `EquipmentState.get_tag()` public 메서드 추가 + `_by_name` 외부 접근 제거 (§4.3)
3. `sim/log.py` configure 통합 (§4.4)

**Day 2**:
4. `sim/protocols/_base.py` 로 공통 패턴 추출 (§4.1)
5. `sim/codec.py` 로 coerce 통합 (§4.5 의 일부)

**Day 3**:
6. `tests/test_config.py`, `test_state.py`, `test_codec.py` 작성 (§4.6)
7. CI (간단한 GitHub Actions 또는 로컬 `make test`) 추가

여기까지가 **눈에 보이는 큰 변화 없이 안전한 정리**입니다. 이후 §4.7 의 큰 구조 변경은 새 프로토콜 추가가 실제로 필요해질 때 같이 진행하세요.

---

## 9. 부록 — 자주 보는 코드 패턴 사전

### 9.1 stop_event 패턴
```python
def run(cfg, state, stop_event: threading.Event):
    period = cfg.sampling_ms / 1000.0
    while not stop_event.wait(period):
        state.tick()
        push_to_datastore(state, ...)
```
`stop_event.wait(period)` 가 timeout 까지 대기 후 `False` 리턴 → 시그널 받으면 즉시 `True` 리턴. 별도 sleep 인터럽트 처리 불필요.

### 9.2 asyncio + threading 다리
```python
stopper = asyncio.create_task(asyncio.to_thread(stop_event.wait))
_done, pending = await asyncio.wait(
    {server_task, updater_task, stopper},
    return_when=asyncio.FIRST_COMPLETED,
)
for t in pending: t.cancel()
```
asyncio 안에서 threading.Event 를 await 가능하게 만드는 표준 패턴.

### 9.3 float ↔ 2 word
```python
# big-endian (Modbus 표준): hi, lo
hi, lo = struct.unpack(">HH", struct.pack(">f", value))
value = struct.unpack(">f", struct.pack(">HH", hi, lo))[0]

# little-endian (MC Protocol 통례): lo, hi
lo, hi = struct.unpack("<HH", struct.pack("<f", value))
value = struct.unpack("<f", struct.pack("<HH", lo, hi))[0]
```
**byte order 와 word order 는 별개** 입니다. 위 두 가지가 가장 흔하지만 PLC 마다 다를 수 있어요. 검증할 때는 `1.0` 을 넣어보고 `0x3F80 0000` 또는 `0x0000 3F80` 어느 쪽으로 나오는지 봐서 결정.

### 9.4 환경변수 전개
```python
"${LINE_ID:-LINE-00}_CAST-01"
```
- `LINE_ID` 환경변수 있으면 그 값, 없으면 `LINE-00`.
- bash 와 동일 syntax. config 안 어디서든 사용 가능 (재귀 전개).
- `sim/config.py::_expand_env` 가 구현.

---

문서가 늘어졌네요. 막막하면 §1과 §4 만 다시 읽어보세요. 시작점은 결국 "**state 가 단일 진실의 원천이고, 프로토콜은 얇은 어댑터**" 한 줄입니다.
