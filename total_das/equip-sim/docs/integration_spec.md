# 라인 DAS 통합 규격

작성일: 2026-05-13 (개정 2)
대상: equip-sim 시뮬레이터 + Node-RED 라인 DAS + 상위 X-DAS

---

## 1. 전체 구성

```
[설비 27대]                    [라인 DAS 3개]              [X-DAS]
LINE-01 × 9 ──┐
LINE-02 × 9 ──┤  ── 폴링 ──▶  Node-RED line das  ── OPC UA Server ──▶  X-DAS
LINE-03 × 9 ──┘                  (라인별 1개)         (TCP)
```

- 설비 측: 시뮬레이터가 각 설비 1대의 PLC를 흉내냄 (Modbus / MC Protocol / OPC UA)
- 라인 DAS: 라인 1개당 Node-RED 인스턴스 1개. 9대를 폴링해 라인 상태를 모음.
- 상위 통신: 라인 DAS가 OPC UA Server를 켜서 X-DAS에 노출. 정해진 NodeId 규약을 따름.

## 2. 라인 구성

각 라인은 동일한 9대 구성:

| 설비 | 인스턴스 ID | 프로토콜 | 비고 |
|---|---|---|---|
| 주조기 | `CAST-01` | Modbus TCP | 1대 |
| 가공기 | `CNC-01` | **MC Protocol 3E Binary** | 1대 |
| 가공기 | `CNC-02`, `CNC-03` | **OPC UA** | 2대 |
| 세척기 | `WASH-01` | OPC UA | 1대 |
| 조립기 | `ASSY-01`, `ASSY-02` | OPC UA | 2대 |
| 검사기 | `TEST-01`, `TEST-02` | OPC UA | 2대 |

> 변경: 이전 명세에서는 CNC-01/02/03 모두 MC Protocol 이었으나
> **CNC-01 만 MC Protocol**, CNC-02/03 은 OPC UA 로 통일되었습니다.

## 3. 포트 매핑

`base + 100 × (line-1)` 규칙. 라인끼리 호스트 포트 충돌 없음.

| 라인 | CAST-01 Modbus | CNC-01 MC | CNC-02/03 OPC UA | WASH-01 OPC UA | ASSY-01/02 OPC UA | TEST-01/02 OPC UA | 라인 DAS OPC UA | Node-RED UI |
|---|---|---|---|---|---|---|---|---|
| LINE-01 | 5021 | 5081 | 5082 / 5083 | 4841 | 4851 / 4852 | 4861 / 4862 | **4870** | 1880 |
| LINE-02 | 5121 | 5181 | 5182 / 5183 | 4941 | 4951 / 4952 | 4961 / 4962 | **4970** | 1881 |
| LINE-03 | 5221 | 5281 | 5282 / 5283 | 5041 | 5051 / 5052 | 5061 / 5062 | **5070** | 1882 |

## 4. 태그 명세

태그명은 **모든 설비에서 동일한 의미면 동일한 이름**을 사용합니다. 공정값은 설비별로 다릅니다.

### 4.1 공통 태그 (모든 설비)

| 태그명 | 단위 | 타입 | RW | 비고 |
|---|---|---|---|---|
| `power` | bool | bool | RW | ON/OFF 제어 |
| `progress` | (없음) | float | RO | **단위 시간당 진행률**. base = `1/cycle_sec`, stddev = base × 10% |

> 이전 명세의 `voltage`, `current`, `surface_temperature`, `vibration`, `alarm`,
> `part_count`, `cycle_time` 은 모두 제거되었습니다.
> `cycle_time` 은 더 이상 센서가 아니며, Node-RED 라인 DAS 가 `progress` 누적에서
> 계산해 페이로드에 포함시킵니다 (§ 5 참고).

**설비별 cycle_sec / progress base**

| 설비 | cycle_sec | progress base |
|---|---|---|
| CAST-01 | 60 | 1/60 |
| CNC-01/02/03 | 180 | 1/180 |
| WASH-01 | 60 | 1/60 |
| ASSY-01/02 | 120 | 1/120 |
| TEST-01/02 | 120 | 1/120 |

### 4.2 설비별 공정 태그

**CAST-01 주조기**
| 태그명 | 단위 | 타입 | RW | RUN 범위 |
|---|---|---|---|---|
| `injection_pressure` | MPa | float | RO | 30~120 |
| `mold_temperature` | ℃ | float | RO | 190~240 |
| `cooling_flow` | L/min | float | RO | 20~60 |
| `injection_pressure_sp` | MPa | float | RW | 설정값 |
| `mold_temperature_sp` | ℃ | float | RW | 설정값 |

**CNC-01/02/03 가공기**
| 태그명 | 단위 | 타입 | RW | RUN 범위 |
|---|---|---|---|---|
| `spindle_speed` | rpm | int | RO | 3000~8000 |
| `tool_usage` | % | float | RO | 0~80 |
| `coolant_flow` | L/min | float | RO | 10~30 |
| `spindle_speed_sp` | rpm | int | RW | 설정값 |

**WASH-01 세척기**
| 태그명 | 단위 | 타입 | RW | RUN 범위 |
|---|---|---|---|---|
| `cleaning_concentration` | % | float | RO | 2~5 |
| `temperature` | ℃ | float | RO | 50~75 |
| `pressure` | bar | float | RO | 2~6 |
| `flow` | L/min | float | RO | 20~80 |
| `cleaning_temperature_sp` | ℃ | float | RW | 설정값 |

**ASSY-01/02 조립기**
| 태그명 | 단위 | 타입 | RW | RUN 범위 |
|---|---|---|---|---|
| `tightening_torque` | Nm | float | RO | 30~50 |
| `angle` | deg | float | RO | 설정 범위 내 |
| `press_force` | N | float | RO | 500~3000 |
| `part_detected` | bool | bool | RO | 부품 감지 |
| `tightening_torque_sp` | Nm | float | RW | 설정값 |

**TEST-01/02 검사기**
| 태그명 | 단위 | 타입 | RW | RUN 범위 |
|---|---|---|---|---|
| `bore_dimension` | mm | float | RO | 40.000 ± 0.020 |
| `hole_dimension` | mm | float | RO | 10.200 ± 0.050 |
| `leak_rate` | cc/min | float | RO | 기준 이하 |
| `flow_value` | L/min | float | RO | 기준 내 |
| `result_ok` | bool | bool | RO | OK=true / NG=false |

> TEST 설비에는 SP 가 없습니다.

### 4.3 MC Protocol 매핑 (CNC-01 전용)

| 주소 | 태그 | 타입 |
|---|---|---|
| `M0` | `power` | bool |
| `D0` | `spindle_speed_sp` | float (2 word, LSB-first) |
| `D2` | `spindle_speed` | float |
| `D100` | `tool_usage` | float |
| `D102` | `coolant_flow` | float |
| `D104` | `progress` | float |

### 4.4 외부 환경 센서 (보류)

`ambient_temperature`, `humidity` 는 ENV-01 같은 외부 센서가 별도 DAS 로 흘리므로
본 시뮬레이터 태그에는 포함하지 않습니다.

## 5. 페이로드 규격

라인 DAS 가 X-DAS 에 올리는 페이로드 (1 Hz):

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
        "mold_temperature_sp": 215.0
      }
    },
    "CNC-01": { "status": "RUN", "ts": "...", "quality": "GOOD",
                "data": { "power": true, "progress": 0.0056,
                          "cycle_time": 178.3,
                          "spindle_speed": 5200, "tool_usage": 42.1,
                          "coolant_flow": 18.4, "spindle_speed_sp": 5000 }
              },
    "...": "...",
    "TEST-02": { "status": "OFF", "ts": "...", "quality": "GOOD",
                 "data": { "power": false } }
  }
}
```

### 필드 정의

| 키 | 타입 | 설명 |
|---|---|---|
| `ts` | string | ISO-8601 UTC, ms 정밀도. 라인 DAS 페이로드 생성 시각 |
| `line_id` | string | `LINE-01`, `LINE-02`, `LINE-03` |
| `schema_version` | string | 본 규격 버전 |
| `equipments.<ID>.status` | string | `OFF` / `RUN` (`WARNING`/`DANGER` 향후 확장) |
| `equipments.<ID>.ts` | string | 해당 설비 데이터 측정 시각 |
| `equipments.<ID>.quality` | string | `GOOD` / `UNCERTAIN` / `BAD` |
| `equipments.<ID>.data` | object | 태그명 → 값. 공통 + 공정 태그 평탄 구조 |
| `equipments.<ID>.data.cycle_time` | float | 직전 cycle 소요(초). 라인 DAS 가 `progress` 누적으로 산출. 1회도 완료 전이면 키 없음 |

### 5.1 status 판정 (라인 DAS / Node-RED)

라인 DAS function 노드가 시뮬레이터 응답으로부터 산출합니다.

1. `data` 비었거나 통신 실패 → `OFF` + `quality=BAD`
2. `power === false` → `OFF`
3. 그 외 → `RUN`
4. `WARNING` / `DANGER` 는 명세상 자리만 잡혀 있으며 현재 구현은 RUN 한 단계만 사용

### 5.2 quality 판정

| 값 | 조건 |
|---|---|
| `GOOD` | 마지막 수신이 3 초 이내 |
| `UNCERTAIN` | 마지막 수신이 3 초 초과, 그러나 일부 데이터는 존재 |
| `BAD` | 데이터 없음 (폴링 자체 실패) |

### 5.3 cycle_time 산출 (라인 DAS / Node-RED)

`progress` 는 단위 시간당 진행률입니다. 라인 DAS function 노드에서
flow context (`cycle_state[<EQ>] = { acc, last_cycle_time, start_ms }`) 로 누적합니다.

- 매 tick `acc += progress`. `start_ms` 가 없으면 `Date.now()` 로 초기화.
- `acc >= 1.0` 인 순간 cycle 1회 완료 →
  `last_cycle_time = (Date.now() - start_ms) / 1000`,
  `acc=0`, `start_ms=null` 로 리셋.
- 페이로드의 `cycle_time` 필드는 `last_cycle_time` 값. 한 번도 완료 전이면 필드 생략.

## 6. 라인 DAS → X-DAS OPC UA 노출

### NodeId 규약

```
ns=2;s=<line_id>.payload                (String, 통째 JSON)
ns=2;s=<line_id>.line_ts                (String)
ns=2;s=<line_id>.schema_version         (String)
ns=2;s=<line_id>.<equipment_id>.status  (String)
ns=2;s=<line_id>.<equipment_id>.ts      (String)
ns=2;s=<line_id>.<equipment_id>.quality (String)
ns=2;s=<line_id>.<equipment_id>.data.<tag_name>  (Boolean / Float / Int32)
ns=2;s=<line_id>.<equipment_id>.data.cycle_time  (Float, 라인 DAS 가 계산해 data 안에 합쳐 발행)
```

### Endpoint

| 라인 | endpoint |
|---|---|
| LINE-01 | `opc.tcp://<host>:4870/line-das/LINE-01` |
| LINE-02 | `opc.tcp://<host>:4970/line-das/LINE-02` |
| LINE-03 | `opc.tcp://<host>:5070/line-das/LINE-03` |

구현은 `node-red-contrib-opcua` 의 `OpcUa-Server` 노드.

## 7. 폴링 주기

| 항목 | 값 | 비고 |
|---|---|---|
| 설비 측 sampling_ms | 1000 | 시뮬레이터 내부 갱신 주기 |
| 라인 DAS 폴링 주기 | 1000 | Node-RED inject |
| 페이로드 생성 주기 | 1000 | 라인 단위 1초 1건 |
| 통신 timeout | 2000 | 설비 응답 대기 |

## 8. 데이터 흐름 정리

```
                         [LINE-01]
시뮬레이터 9 설비                                  Node-RED 라인 DAS
─────────────────                                  ────────────────
CAST-01  Modbus :5021    ───polling───▶  Modbus client  ┐
CNC-01   MC     :5081    ───polling───▶  MC client      │
CNC-02   OPC UA :5082    ───polling───▶  OPC UA client  │
CNC-03   OPC UA :5083    ───polling───▶  OPC UA client  │
WASH-01  OPC UA :4841    ───polling───▶  OPC UA client  ├──▶ aggregate ──▶ payload(JSON) ──▶ X-DAS
ASSY-01  OPC UA :4851    ───polling───▶  OPC UA client  │            └──▶ OPC UA Server :4870
ASSY-02  OPC UA :4852    ───polling───▶  OPC UA client  │
TEST-01  OPC UA :4861    ───polling───▶  OPC UA client  │
TEST-02  OPC UA :4862    ───polling───▶  OPC UA client  ┘
```

## 9. 향후 확장

- `WARNING` / `DANGER` 임계 룰셋을 라인 DAS function 에 추가
- 환경 센서 DAS 별도 구현 시 본 페이로드와 동일 schema 로 `LINE-XX.ENV-01` 추가
- schema_version `1.1` 로 환경 센서 + KPI(가동률, OEE) 필드 추가
