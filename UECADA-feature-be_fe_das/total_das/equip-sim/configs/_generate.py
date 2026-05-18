"""3개 라인 × 9개 설비 = 27개 config 일괄 생성기.

명세서 (docs/integration_spec.md / taegeu-myeongseseo.md) 반영:

- 공통 태그: power (bool, RW), progress (float, sensor, 기준 1/cycle_sec)
  * progress 는 매 tick 마다 base ± N(0, 10%×base) 생성. Node-RED 가 누적해서
    cycle_time 을 계산하므로 시뮬레이터는 단순 센서값만 송출.
- 설비별 공정 태그:
    CAST-01  Modbus   injection_pressure / mold_temperature / cooling_flow
                       + sp 2개 (injection_pressure_sp, mold_temperature_sp)
    CNC-01   MC       spindle_speed / tool_usage / coolant_flow
                       + sp 1개 (spindle_speed_sp)
    CNC-02/03 OPC UA  (위 CNC 태그 셋 동일, 프로토콜만 OPC UA)
    WASH-01  OPC UA  cleaning_concentration / cleaning_temperature /
                       cleaning_pressure / cleaning_flow
                       + sp 1개 (cleaning_temperature_sp)
    ASSY-01/02 OPC UA tightening_torque / tightening_angle / press_force /
                       part_detected (bool, RO)
                       + sp 1개 (tightening_torque_sp)
    TEST-01/02 OPC UA bore_dimension / hole_dimension / leak_rate /
                       flow_value / result_ok (bool, RO)
                       (SP 없음)

- alarm 태그 / part_count 태그 / voltage/current/vibration 등 전기·기계 공통
  태그는 모두 제거됨.
- status / cycle_time / quality 는 Node-RED 가 산출. 시뮬레이터에는 없음.

포트 정책 (명세서 §3): LINE-N 의 포트 = base + (N-1) × 100
  CAST-01: 5021/5121/5221
  CNC-01:  5081/5181/5281    (MC)
  CNC-02:  5082/5182/5282    (OPC UA)
  CNC-03:  5083/5183/5283    (OPC UA)
  WASH-01: 4841/4941/5041
  ASSY-01: 4851/4951/5051
  ASSY-02: 4852/4952/5052
  TEST-01: 4861/4961/5061
  TEST-02: 4862/4962/5062

OPC UA Server (Node-RED 라인 DAS): 4870/4970/5070
"""
from __future__ import annotations

import json
from pathlib import Path

OUT_ROOT = Path(__file__).parent


# ---------------------------------------------------------------------------
# 공통 태그
#
# progress 는 sensor role 이지만 source_sp 가 명세에 없어서 base_value 를
# 기준값으로 직접 사용한다 (state.py 가 source_sp 가 없으면 base_value 로 fallback).
# ---------------------------------------------------------------------------

def power_tag() -> dict:
    return {
        "name": "power",
        "role": "power",
        "data_type": "bool",
        "base_value": True,
    }


def progress_tag(cycle_sec: int) -> dict:
    base = 1.0 / cycle_sec
    return {
        "name": "progress",
        "role": "sensor",
        "data_type": "float",
        "base_value": base,
        "stddev": base * 0.10,   # 10 % 변동
    }


# ---------------------------------------------------------------------------
# 설비별 공정 태그 (모두 sensor + setpoint 평탄 dict)
# ---------------------------------------------------------------------------

def cast_process_tags() -> list[dict]:
    # injection_pressure  30~120 MPa, mold_temperature 190~240 ℃, cooling_flow 20~60 L/min
    return [
        {"name": "injection_pressure_sp", "role": "setpoint", "data_type": "float", "base_value": 80.0},
        {"name": "mold_temperature_sp",   "role": "setpoint", "data_type": "float", "base_value": 215.0},
        {"name": "injection_pressure", "role": "sensor", "data_type": "float",
         "source_sp": "injection_pressure_sp", "stddev": 1.5},
        {"name": "mold_temperature",   "role": "sensor", "data_type": "float",
         "source_sp": "mold_temperature_sp",   "stddev": 1.2},
        {"name": "cooling_flow",       "role": "sensor", "data_type": "float",
         "base_value": 40.0, "stddev": 1.5},
    ]


def cnc_process_tags() -> list[dict]:
    # spindle_speed 3000~8000 rpm (int), tool_usage 0~80 %, coolant_flow 10~30 L/min
    return [
        {"name": "spindle_speed_sp", "role": "setpoint", "data_type": "int",   "base_value": 5500},
        {"name": "spindle_speed", "role": "sensor", "data_type": "int",
         "source_sp": "spindle_speed_sp", "stddev": 25.0},
        {"name": "tool_usage",   "role": "sensor", "data_type": "float",
         "base_value": 30.0, "stddev": 1.5},
        {"name": "coolant_flow", "role": "sensor", "data_type": "float",
         "base_value": 18.0, "stddev": 0.8},
    ]


def wash_process_tags() -> list[dict]:
    # cleaning_concentration 2~5 %, cleaning_temperature 50~75 ℃,
    # cleaning_pressure 2~6 bar, cleaning_flow 20~80 L/min
    return [
        {"name": "cleaning_temperature_sp", "role": "setpoint", "data_type": "float", "base_value": 62.0},
        {"name": "cleaning_concentration", "role": "sensor", "data_type": "float",
         "base_value": 3.2, "stddev": 0.15},
        {"name": "cleaning_temperature",   "role": "sensor", "data_type": "float",
         "source_sp": "cleaning_temperature_sp", "stddev": 1.0},
        {"name": "cleaning_pressure",      "role": "sensor", "data_type": "float",
         "base_value": 4.0, "stddev": 0.20},
        {"name": "cleaning_flow",          "role": "sensor", "data_type": "float",
         "base_value": 50.0, "stddev": 2.5},
    ]


def assy_process_tags() -> list[dict]:
    # tightening_torque 30~50 Nm, tightening_angle deg, press_force 500~3000 N,
    # part_detected bool RO
    return [
        {"name": "tightening_torque_sp", "role": "setpoint", "data_type": "float", "base_value": 40.0},
        {"name": "tightening_torque", "role": "sensor", "data_type": "float",
         "source_sp": "tightening_torque_sp", "stddev": 0.5},
        {"name": "tightening_angle",  "role": "sensor", "data_type": "float",
         "base_value": 90.0, "stddev": 2.0},
        {"name": "press_force",       "role": "sensor", "data_type": "float",
         "base_value": 1800.0, "stddev": 60.0},
        {"name": "part_detected",     "role": "sensor", "data_type": "bool",
         "base_value": True, "stddev": 0.0},
    ]


def test_process_tags() -> list[dict]:
    # bore_dimension 40.000 ± 0.020 mm, hole_dimension 10.200 ± 0.050 mm,
    # leak_rate cc/min (기준 이하), flow_value L/min (기준 내), result_ok bool RO
    return [
        {"name": "bore_dimension", "role": "sensor", "data_type": "float",
         "base_value": 40.000, "stddev": 0.010},
        {"name": "hole_dimension", "role": "sensor", "data_type": "float",
         "base_value": 10.200, "stddev": 0.025},
        {"name": "leak_rate",      "role": "sensor", "data_type": "float",
         "base_value": 0.5, "stddev": 0.08},
        {"name": "flow_value",     "role": "sensor", "data_type": "float",
         "base_value": 15.0, "stddev": 0.5},
        {"name": "result_ok",      "role": "sensor", "data_type": "bool",
         "base_value": True, "stddev": 0.0},
    ]


# ---------------------------------------------------------------------------
# 설비 사양 (한 라인 기준)
#
#   (equipment_id, protocol, base_port, process_fn, cycle_sec)
#
# base_port 는 LINE-01 기준. LINE-N 는 base_port + 100*(N-1).
# cycle_sec 은 progress 의 분모 (base = 1 / cycle_sec).
# ---------------------------------------------------------------------------

EQUIPMENT_SPECS = [
    ("CAST-01", "modbus",     5021, cast_process_tags,  60),

    ("CNC-01",  "mcprotocol", 5081, cnc_process_tags,   180),
    ("CNC-02",  "opcua",      5082, cnc_process_tags,   180),
    ("CNC-03",  "opcua",      5083, cnc_process_tags,   180),

    ("WASH-01", "opcua",      4841, wash_process_tags,  60),

    ("ASSY-01", "opcua",      4851, assy_process_tags,  120),
    ("ASSY-02", "opcua",      4852, assy_process_tags,  120),

    ("TEST-01", "opcua",      4861, test_process_tags,  120),
    ("TEST-02", "opcua",      4862, test_process_tags,  120),
]

LINES = (1, 2, 3)
PORT_STRIDE = 100


# ---------------------------------------------------------------------------
# MC Protocol 매핑 (CNC-01 전용 — 명세상 CNC-02/03 은 OPC UA)
#
#   M0   power  (bit, RW)
#   D0   spindle_speed_sp     int    (RW)
#   D2   spindle_speed        int
#   D100 tool_usage           float  (D100..D101)
#   D102 coolant_flow         float  (D102..D103)
#   D104 progress             float  (D104..D105)
# ---------------------------------------------------------------------------

MC_MAPPING_CNC = {
    "power":            {"device": "M", "address": 0},
    "spindle_speed_sp": {"device": "D", "address": 0},
    "spindle_speed":    {"device": "D", "address": 2},
    "tool_usage":       {"device": "D", "address": 100},
    "coolant_flow":     {"device": "D", "address": 102},
    "progress":         {"device": "D", "address": 104},
}


# ---------------------------------------------------------------------------
# config 빌드
# ---------------------------------------------------------------------------

def build_tags(process_tags: list[dict], protocol: str,
               cycle_sec: int) -> list[dict]:
    tags = [power_tag()] + process_tags + [progress_tag(cycle_sec)]
    if protocol == "mcprotocol":
        for t in tags:
            mc = MC_MAPPING_CNC.get(t["name"])
            if mc is None:
                raise KeyError(
                    f"MC_MAPPING_CNC missing '{t['name']}' "
                    f"(필요한 모든 CNC tag 는 mc 매핑이 있어야 합니다)"
                )
            t["mc"] = mc
    return tags


def build_config(equipment_id: str, protocol: str, port: int,
                 process_fn, cycle_sec: int) -> dict:
    cfg = {
        "equipment_name": "${LINE_ID:-LINE-00}_" + equipment_id,
        "protocol": protocol,
        "host": "0.0.0.0",
        "port": port,
        "sampling_ms": 1000,
        "tags": build_tags(process_fn(), protocol, cycle_sec),
    }
    if protocol == "opcua":
        cfg["namespace"] = "${LINE_ID:-LINE-00}_" + equipment_id
    return cfg


def main() -> None:
    written: list[Path] = []
    for ln in LINES:
        line_dir = OUT_ROOT / f"line{ln}"
        line_dir.mkdir(exist_ok=True)
        for eq_id, proto, base_port, fn, cycle_sec in EQUIPMENT_SPECS:
            port = base_port + (ln - 1) * PORT_STRIDE
            cfg = build_config(eq_id, proto, port, fn, cycle_sec)
            p = line_dir / f"{eq_id}.json"
            p.write_text(json.dumps(cfg, indent=2, ensure_ascii=False),
                         encoding="utf-8")
            written.append(p)
    for p in written:
        print(f"wrote {p.relative_to(OUT_ROOT.parent)}")
    print(f"total {len(written)} files")


if __name__ == "__main__":
    main()
