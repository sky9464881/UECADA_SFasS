"""3개 라인 × 9개 설비 = 27개 config 일괄 생성기.

README.md (2026-05-13 개정 3) 반영:

- 공통 태그: power (bool, RW), progress (float, sensor, 기준 1/cycle_sec)
  * progress 는 매 tick 마다 base ± N(0, 10%×base) 생성. Node-RED 가 누적해서
    cycle_time 을 계산하므로 시뮬레이터는 단순 센서값만 송출.

- 설비별 공정 태그:
    CAST-01   MC Protocol (TCP)  injection_pressure / mold_temperature / cooling_flow
                                  + sp 3개
    CNC-01/02/03  Modbus RTU      spindle_speed / tool_usage / coolant_flow
                                  + sp 3개   (시리얼 디바이스)
    WASH-01   Modbus TCP          cleaning_concentration / cleaning_temperature /
                                  cleaning_pressure
                                  + sp 3개
    ASSY-01/02  OPC UA            tightening_torque / tightening_angle / press_force
                                  + sp 3개
    TEST-01/02  OPC UA            bore_dimension / hole_dimension / result_ok (bool RO)
                                  + sp 2개  (bore_sp, hole_sp)

- 호스트 포트 (TCP, base + 100*(N-1)):
    CAST-01: 5001 / 5101 / 5201   (MC)
    WASH-01: 5021 / 5121 / 5221   (Modbus TCP)
    ASSY-01: 4841 / 4941 / 5041   (OPC UA)
    ASSY-02: 4842 / 4942 / 5042
    TEST-01: 4851 / 4951 / 5051
    TEST-02: 4852 / 4952 / 5052
    DAS OPCUA: 4860 / 4960 / 5060
    Node-RED UI: 2880 / 3880 / 4880

- CNC-01/02/03 는 포트 없음. 시리얼 디바이스 경로만 가짐:
    /dev/cnc01.slave  /dev/cnc02.slave  /dev/cnc03.slave
  (Node-RED 쪽은 /dev/cnc0X.master)
"""
from __future__ import annotations

import json
from pathlib import Path

OUT_ROOT = Path(__file__).parent


# ---------------------------------------------------------------------------
# 공통 태그
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
        {"name": "cooling_flow_sp",       "role": "setpoint", "data_type": "float", "base_value": 40.0},
        {"name": "injection_pressure", "role": "sensor", "data_type": "float",
         "source_sp": "injection_pressure_sp", "stddev": 1.5},
        {"name": "mold_temperature",   "role": "sensor", "data_type": "float",
         "source_sp": "mold_temperature_sp",   "stddev": 1.2},
        {"name": "cooling_flow",       "role": "sensor", "data_type": "float",
         "source_sp": "cooling_flow_sp", "stddev": 1.5},
    ]


def cnc_process_tags() -> list[dict]:
    # spindle_speed 3000~8000 rpm (int), tool_usage 0~80 %, coolant_flow 10~30 L/min
    return [
        {"name": "spindle_speed_sp", "role": "setpoint", "data_type": "int",   "base_value": 5500},
        {"name": "tool_usage_sp",    "role": "setpoint", "data_type": "float", "base_value": 30.0},
        {"name": "coolant_flow_sp",  "role": "setpoint", "data_type": "float", "base_value": 18.0},
        {"name": "spindle_speed", "role": "sensor", "data_type": "int",
         "source_sp": "spindle_speed_sp", "stddev": 25.0},
        {"name": "tool_usage",   "role": "sensor", "data_type": "float",
         "source_sp": "tool_usage_sp",   "stddev": 1.5},
        {"name": "coolant_flow", "role": "sensor", "data_type": "float",
         "source_sp": "coolant_flow_sp", "stddev": 0.8},
    ]


def wash_process_tags() -> list[dict]:
    # cleaning_concentration 2~5 %, cleaning_temperature 50~75 ℃, cleaning_pressure 2~6 bar
    return [
        {"name": "cleaning_concentration_sp", "role": "setpoint", "data_type": "float", "base_value": 3.2},
        {"name": "cleaning_temperature_sp",   "role": "setpoint", "data_type": "float", "base_value": 62.0},
        {"name": "cleaning_pressure_sp",      "role": "setpoint", "data_type": "float", "base_value": 4.0},
        {"name": "cleaning_concentration", "role": "sensor", "data_type": "float",
         "source_sp": "cleaning_concentration_sp", "stddev": 0.15},
        {"name": "cleaning_temperature",   "role": "sensor", "data_type": "float",
         "source_sp": "cleaning_temperature_sp",   "stddev": 1.0},
        {"name": "cleaning_pressure",      "role": "sensor", "data_type": "float",
         "source_sp": "cleaning_pressure_sp", "stddev": 0.20},
    ]


def assy_process_tags() -> list[dict]:
    # tightening_torque 30~50 Nm, tightening_angle deg, press_force 500~3000 N
    return [
        {"name": "tightening_torque_sp", "role": "setpoint", "data_type": "float", "base_value": 40.0},
        {"name": "tightening_angle_sp",  "role": "setpoint", "data_type": "float", "base_value": 90.0},
        {"name": "press_force_sp",       "role": "setpoint", "data_type": "float", "base_value": 1800.0},
        {"name": "tightening_torque", "role": "sensor", "data_type": "float",
         "source_sp": "tightening_torque_sp", "stddev": 0.5},
        {"name": "tightening_angle",  "role": "sensor", "data_type": "float",
         "source_sp": "tightening_angle_sp", "stddev": 2.0},
        {"name": "press_force",       "role": "sensor", "data_type": "float",
         "source_sp": "press_force_sp", "stddev": 60.0},
    ]


def test_process_tags() -> list[dict]:
    # bore_dimension 40.000 ± 0.020 mm, hole_dimension 10.200 ± 0.050 mm,
    # result_ok bool RO. (leak_rate / flow_value 제거)
    return [
        {"name": "bore_dimension_sp", "role": "setpoint", "data_type": "float", "base_value": 40.000},
        {"name": "hole_dimension_sp", "role": "setpoint", "data_type": "float", "base_value": 10.200},
        {"name": "bore_dimension", "role": "sensor", "data_type": "float",
         "source_sp": "bore_dimension_sp", "stddev": 0.010},
        {"name": "hole_dimension", "role": "sensor", "data_type": "float",
         "source_sp": "hole_dimension_sp", "stddev": 0.025},
        {"name": "result_ok",      "role": "sensor", "data_type": "bool",
         "base_value": True, "stddev": 0.0},
    ]


# ---------------------------------------------------------------------------
# 설비 사양 (한 라인 기준)
#
#   (equipment_id, protocol, base_port_or_None, process_fn, cycle_sec, serial_slot)
#
# - base_port: TCP 기반 설비의 호스트 포트 (LINE-01 기준). LINE-N 는 +100*(N-1).
# - CNC-01/02/03 은 RTU 시리얼이므로 base_port=None, serial_slot=1/2/3 사용.
#   serial 경로: /dev/cnc0{slot}.slave (시뮬)  /dev/cnc0{slot}.master (Node-RED)
# ---------------------------------------------------------------------------

EQUIPMENT_SPECS = [
    # (eq_id,     protocol,         base_port, process_fn,         cycle_sec, serial_slot)
    ("CAST-01",  "mcprotocol",      5001,      cast_process_tags,  60,        None),

    # CNC 는 RTU-over-TCP (Moxa NPort 스타일) 으로 시뮬이 직접 TCP listen.
    # base_port 는 컨테이너 내부 포트 (5101/02/03) — 라인 무관하게 동일.
    ("CNC-01",   "modbus-rtu-tcp",  5101,      cnc_process_tags,   180,       1),
    ("CNC-02",   "modbus-rtu-tcp",  5102,      cnc_process_tags,   180,       2),
    ("CNC-03",   "modbus-rtu-tcp",  5103,      cnc_process_tags,   180,       3),

    ("WASH-01",  "modbus",          5021,      wash_process_tags,  60,        None),

    ("ASSY-01",  "opcua",           4841,      assy_process_tags,  120,       None),
    ("ASSY-02",  "opcua",           4842,      assy_process_tags,  120,       None),

    ("TEST-01",  "opcua",           4851,      test_process_tags,  120,       None),
    ("TEST-02",  "opcua",           4852,      test_process_tags,  120,       None),
]

LINES = (1, 2, 3)
PORT_STRIDE = 100

# DAS / 노드레드 UI 베이스 포트
DAS_OPCUA_BASE = 4860
NODERED_UI_BASE = 2880   # 라인별 stride=1000 (2880 / 3880 / 4880) — 다른 규칙


def line_port(base_port: int, line_no: int) -> int:
    return base_port + (line_no - 1) * PORT_STRIDE


def das_opcua_port(line_no: int) -> int:
    return DAS_OPCUA_BASE + (line_no - 1) * PORT_STRIDE


def nodered_ui_port(line_no: int) -> int:
    # 2880 / 3880 / 4880  → stride 1000
    return NODERED_UI_BASE + (line_no - 1) * 1000


def cnc_serial_slave(slot: int) -> str:
    """시뮬레이터 쪽 RTU 슬레이브 디바이스 경로 (compose 에서 mount).

    /dev/vserial 공유 볼륨에 socat 가 심볼릭 링크를 떨괴다.
    """
    return f"/dev/vserial/cnc0{slot}.slave"


def cnc_serial_master(slot: int) -> str:
    """Node-RED 쪽 RTU 마스터 디바이스 경로."""
    return f"/dev/vserial/cnc0{slot}.master"


# ---------------------------------------------------------------------------
# MC Protocol 매핑 (CAST-01 전용)
#
#   M0    power               (bit, RW)
#   D0    injection_pressure_sp  float (2 word)  -> D0,D1
#   D2    mold_temperature_sp    float           -> D2,D3
#   D4    cooling_flow_sp        float           -> D4,D5
#   D100  injection_pressure     float           -> D100,D101
#   D102  mold_temperature       float           -> D102,D103
#   D104  cooling_flow           float           -> D104,D105
#   D106  progress               float           -> D106,D107
# ---------------------------------------------------------------------------

MC_MAPPING_CAST = {
    "power":                  {"device": "M", "address": 0},
    "injection_pressure_sp":  {"device": "D", "address": 0},
    "mold_temperature_sp":    {"device": "D", "address": 2},
    "cooling_flow_sp":        {"device": "D", "address": 4},
    "injection_pressure":     {"device": "D", "address": 100},
    "mold_temperature":       {"device": "D", "address": 102},
    "cooling_flow":           {"device": "D", "address": 104},
    "progress":               {"device": "D", "address": 106},
}


# ---------------------------------------------------------------------------
# Modbus (TCP/RTU 공통) 매핑 강제 — coil/HR 절대 주소를 config 에 박는다.
#
# CNC-01/02/03 (Modbus RTU):
#   Coil 0      power
#   HR    0     spindle_speed_sp        (uint16)
#   HR    2     spindle_speed           (uint16)
#   HR  1000    tool_usage_sp           (float, 2 word, big-endian)
#   HR  1002    tool_usage              (float)
#   HR  1004    coolant_flow_sp         (float)
#   HR  1006    coolant_flow            (float)
#   HR  1008    progress                (float)
#
# WASH-01 (Modbus TCP):
#   Coil 0      power
#   HR  1000    cleaning_concentration_sp  (float)
#   HR  1002    cleaning_temperature_sp    (float)
#   HR  1004    cleaning_pressure_sp       (float)
#   HR  1006    cleaning_concentration     (float)
#   HR  1008    cleaning_temperature       (float)
#   HR  1010    cleaning_pressure          (float)
#   HR  1012    progress                   (float)
# ---------------------------------------------------------------------------

MB_MAPPING_CNC = {
    "power":            {"kind": "coil",     "address": 0},
    "spindle_speed_sp": {"kind": "hr_int",   "address": 0},
    "spindle_speed":    {"kind": "hr_int",   "address": 2},
    "tool_usage_sp":    {"kind": "hr_float", "address": 1000},
    "tool_usage":       {"kind": "hr_float", "address": 1002},
    "coolant_flow_sp":  {"kind": "hr_float", "address": 1004},
    "coolant_flow":     {"kind": "hr_float", "address": 1006},
    "progress":         {"kind": "hr_float", "address": 1008},
}

MB_MAPPING_WASH = {
    "power":                       {"kind": "coil",     "address": 0},
    "cleaning_concentration_sp":   {"kind": "hr_float", "address": 1000},
    "cleaning_temperature_sp":     {"kind": "hr_float", "address": 1002},
    "cleaning_pressure_sp":        {"kind": "hr_float", "address": 1004},
    "cleaning_concentration":      {"kind": "hr_float", "address": 1006},
    "cleaning_temperature":        {"kind": "hr_float", "address": 1008},
    "cleaning_pressure":           {"kind": "hr_float", "address": 1010},
    "progress":                    {"kind": "hr_float", "address": 1012},
}


# ---------------------------------------------------------------------------
# config 빌드
# ---------------------------------------------------------------------------

def build_tags(process_tags: list[dict], protocol: str, cycle_sec: int,
               eq_id: str) -> list[dict]:
    tags = [power_tag()] + process_tags + [progress_tag(cycle_sec)]

    if protocol == "mcprotocol":
        for t in tags:
            mc = MC_MAPPING_CAST.get(t["name"])
            if mc is None:
                raise KeyError(
                    f"MC_MAPPING_CAST missing '{t['name']}' "
                    f"(CAST 의 모든 tag 는 mc 매핑이 있어야 합니다)"
                )
            t["mc"] = mc

    elif protocol in ("modbus", "modbus-rtu", "modbus-rtu-tcp"):
        if eq_id == "WASH-01":
            mapping = MB_MAPPING_WASH
        elif eq_id.startswith("CNC-"):
            mapping = MB_MAPPING_CNC
        else:
            raise ValueError(f"no modbus mapping for {eq_id}")
        for t in tags:
            mb = mapping.get(t["name"])
            if mb is None:
                raise KeyError(
                    f"Modbus mapping missing '{t['name']}' for {eq_id}"
                )
            t["mb"] = mb

    return tags


def build_config(equipment_id: str, protocol: str, base_port: int | None,
                 process_fn, cycle_sec: int, serial_slot: int | None) -> dict:
    cfg: dict = {
        "equipment_name": "${LINE_ID:-LINE-00}_" + equipment_id,
        "protocol": protocol,
        "sampling_ms": 1000,
        "tags": build_tags(process_fn(), protocol, cycle_sec, equipment_id),
    }

    if protocol == "modbus-rtu":
        # 시리얼 슬레이브 (현재 파이프라인에서는 사용하지 않음 — RTU-over-TCP 로 이동)
        cfg["serial_path"] = cnc_serial_slave(serial_slot)
        cfg["baudrate"] = 9600
        cfg["parity"] = "N"
        cfg["stopbits"] = 1
        cfg["bytesize"] = 8
        cfg["slave_id"] = 1
        cfg["host"] = ""
        cfg["port"] = 0
    elif protocol == "modbus-rtu-tcp":
        # Moxa NPort 스타일: 시뮬이 RTU 프레임을 TCP 소켓으로 직접 서브.
        cfg["host"] = "0.0.0.0"
        cfg["port"] = base_port  # 5101/5102/5103
        cfg["slave_id"] = 1
    else:
        cfg["host"] = "0.0.0.0"
        cfg["port"] = base_port  # LINE_PORT 치환은 안 함 (라인별 디렉터리에 박힌 값 사용)

    if protocol == "opcua":
        cfg["namespace"] = "${LINE_ID:-LINE-00}_" + equipment_id

    return cfg


def main() -> None:
    written: list[Path] = []
    for ln in LINES:
        line_dir = OUT_ROOT / f"line{ln}"
        line_dir.mkdir(exist_ok=True)
        for eq_id, proto, base_port, fn, cycle_sec, serial_slot in EQUIPMENT_SPECS:
            if proto == "modbus-rtu":
                port = None
            elif proto == "modbus-rtu-tcp":
                # CNC 컨테이너 내부 listen 포트는 라인 무관 고정 (5101/02/03)
                port = base_port
            else:
                port = line_port(base_port, ln)
            cfg = build_config(eq_id, proto, port, fn, cycle_sec, serial_slot)
            p = line_dir / f"{eq_id}.json"
            p.write_text(json.dumps(cfg, indent=2, ensure_ascii=False),
                         encoding="utf-8")
            written.append(p)
    for p in written:
        print(f"wrote {p.relative_to(OUT_ROOT.parent)}")
    print(f"total {len(written)} files")


if __name__ == "__main__":
    main()
