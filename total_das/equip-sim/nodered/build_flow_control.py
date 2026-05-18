"""단일 설비 관제/제어 테스트 flow 생성 (수정판).

- node-red-contrib-modbus 노드의 실제 defaults 스키마에만 맞춰 속성 작성
  (유효하지 않은 속성 경고 제거)
- modbus-read 는 자체 폴링(rate + rateUnit) 사용. inject 트리거 안 함
  (modbus-read 노드는 inputs:0 — 외부 트리거 받지 않음)
- modbus-write 는 msg.payload 에 배열(또는 스칼라) 을 직접 전달
  ({value: [...]} 객체로 감싸지 않음)

Modbus(casting_01) + OPC UA(machining_01) 각 1대씩.
"""
from __future__ import annotations

import json
import uuid
from pathlib import Path


def nid() -> str:
    return uuid.uuid4().hex[:16]


CASTING_TAB = nid()
MACHINING_TAB = nid()
MC_TAB = nid()
MODBUS_SERVER = nid()
OPCUA_CLIENT = nid()
MC_CONNECTION = nid()


# ---------------------------------------------------------------------------
# Modbus client config — defaults 에 있는 키만 사용
# https://github.com/biancode/node-red-contrib-modbus modbus-client.html
# ---------------------------------------------------------------------------

def make_modbus_server_config() -> dict:
    return {
        "id": MODBUS_SERVER,
        "type": "modbus-client",
        "name": "casting_01 @ 127.0.0.1:5021",
        "clienttype": "tcp",
        "bufferCommands": True,
        "stateLogEnabled": False,
        "queueLogEnabled": False,
        "failureLogEnabled": True,
        "tcpHost": "127.0.0.1",
        "tcpPort": "5021",
        "tcpType": "DEFAULT",
        "serialPort": "",
        "serialType": "RTU-BUFFERD",
        "serialBaudrate": "9600",
        "serialDatabits": "8",
        "serialStopbits": "1",
        "serialParity": "none",
        "serialConnectionDelay": "100",
        "serialAsciiResponseStartDelimiter": "",
        "unit_id": "1",
        "commandDelay": "1",
        "clientTimeout": "1000",
        "reconnectOnTimeout": True,
        "reconnectTimeout": "2000",
        "parallelUnitIdsAllowed": True,
        "showErrors": False,
        "showWarnings": True,
        "showLogs": True,
    }


def make_opcua_client_config() -> dict:
    return {
        "id": OPCUA_CLIENT,
        "type": "OpcUa-Endpoint",
        "endpoint": "opc.tcp://127.0.0.1:4841/",
        "secpol": "None",
        "secmode": "None",
        "none": True,
        "login": False,
        "usercert": False,
        "usercertificate": "",
        "userprivatekey": "",
    }


# ---------------------------------------------------------------------------
# Modbus read 노드 (defaults 키만 사용)
# ---------------------------------------------------------------------------

def mb_read(z: str, name: str, dtype: str, adr: str, qty: str,
            rate: str = "1", rate_unit: str = "s",
            x: int = 200, y: int = 100,
            out_wires: list | None = None) -> dict:
    return {
        "id": nid(),
        "type": "modbus-read",
        "z": z,
        "name": name,
        "topic": "",
        "showStatusActivities": False,
        "logIOActivities": False,
        "showErrors": True,
        "showWarnings": True,
        "unitid": "",
        "dataType": dtype,
        "adr": adr,
        "quantity": qty,
        "rate": rate,
        "rateUnit": rate_unit,
        "delayOnStart": False,
        "startDelayTime": "",
        "server": MODBUS_SERVER,
        "useIOFile": False,
        "ioFile": "",
        "useIOForPayload": False,
        "emptyMsgOnFail": False,
        "x": x, "y": y,
        "wires": [out_wires or [], []],
    }


def mb_write(z: str, name: str, dtype: str, adr: str, qty: str,
             x: int = 600, y: int = 400) -> dict:
    return {
        "id": nid(),
        "type": "modbus-write",
        "z": z,
        "name": name,
        "showStatusActivities": False,
        "showErrors": True,
        "showWarnings": True,
        "unitid": "",
        "dataType": dtype,
        "adr": adr,
        "quantity": qty,
        "server": MODBUS_SERVER,
        "emptyMsgOnFail": False,
        "keepMsgProperties": False,
        "delayOnStart": False,
        "startDelayTime": "",
        "x": x, "y": y,
        "wires": [[], []],
    }


def inject_node(z: str, name: str, payload: str, ptype: str,
                x: int, y: int, repeat: str = "",
                once: bool = False, once_delay: str = "0.1",
                wires: list | None = None) -> dict:
    return {
        "id": nid(),
        "type": "inject",
        "z": z,
        "name": name,
        "props": [{"p": "payload"}],
        "repeat": repeat,
        "crontab": "",
        "once": once,
        "onceDelay": once_delay,
        "topic": "",
        "payload": payload,
        "payloadType": ptype,
        "x": x, "y": y,
        "wires": [wires or []],
    }


def function_node(z: str, name: str, code: str, x: int, y: int,
                  wires: list | None = None) -> dict:
    return {
        "id": nid(),
        "type": "function",
        "z": z,
        "name": name,
        "func": code,
        "outputs": 1,
        "noerr": 0,
        "initialize": "",
        "finalize": "",
        "libs": [],
        "x": x, "y": y,
        "wires": [wires or []],
    }


def debug_node(z: str, name: str, x: int, y: int) -> dict:
    return {
        "id": nid(),
        "type": "debug",
        "z": z,
        "name": name,
        "active": True,
        "tosidebar": True,
        "console": False,
        "tostatus": False,
        "complete": "payload",
        "targetType": "msg",
        "statusVal": "",
        "statusType": "auto",
        "x": x, "y": y,
        "wires": [],
    }


# ---------------------------------------------------------------------------
# casting_01 (Modbus) 탭
#
# 매핑:
#   coil 0 = power, coil 1 = alarm
#   HR 0 = motor_rpm_sp(int), HR 1 = motor_rpm(int), HR 2 = shot_count(int)
#   HR 1000~1001 melt_temp_sp(float BE)
#   HR 1002~1003 mold_pressure_sp(float BE)
#   HR 1004~1005 cooling_water_temp_sp(float BE)
#   HR 1006~1007 melt_temp(float BE)
#   HR 1008~1009 mold_pressure(float BE)
#   HR 1010~1011 cooling_water_temp(float BE)
# ---------------------------------------------------------------------------

PARSE_COIL = """\
const bits = msg.payload || [];
msg.payload = { power: !!bits[0], alarm: !!bits[1] };
return msg;
"""

PARSE_INT = """\
const r = msg.payload || [];
function s16(v){ v &= 0xFFFF; return v & 0x8000 ? v - 0x10000 : v; }
msg.payload = {
  motor_rpm_sp: s16(r[0]),
  motor_rpm:    s16(r[1]),
  shot_count:   s16(r[2]),
};
return msg;
"""

PARSE_FLOAT = """\
const r = msg.payload || [];
const buf = Buffer.alloc(24);
for (let i = 0; i < 12; i++) buf.writeUInt16BE(r[i] & 0xFFFF, i*2);
const names = [
  'melt_temp_sp','mold_pressure_sp','cooling_water_temp_sp',
  'melt_temp','mold_pressure','cooling_water_temp'
];
const out = {};
names.forEach((n,i)=>{ out[n] = +buf.readFloatBE(i*4).toFixed(2); });
msg.payload = out;
return msg;
"""

# float -> [hi, lo] big-endian 직접 배열로 payload 설정
FLOAT_TO_WORDS = """\
const f = Number(msg.payload);
const buf = Buffer.alloc(4);
buf.writeFloatBE(f, 0);
msg.payload = [buf.readUInt16BE(0), buf.readUInt16BE(2)];
return msg;
"""


def build_casting_nodes() -> list[dict]:
    z = CASTING_TAB
    nodes: list[dict] = []

    # --- 안내 코멘트 ---
    nodes.append({
        "id": nid(), "type": "comment", "z": z,
        "name": "casting_01 (Modbus :5021)",
        "info": (
            "127.0.0.1:5021\n\n"
            "[Read]\n"
            "  coil 0..1 -> power, alarm\n"
            "  HR 0..2   -> motor_rpm_sp, motor_rpm, shot_count (int)\n"
            "  HR 1000..1011 -> 3 setpoint floats + 3 sensor floats (big-endian)\n\n"
            "[Write]\n"
            "  Power ON/OFF -> coil 0\n"
            "  motor_rpm_sp -> HR 0 (스칼라 int)\n"
            "  melt_temp_sp -> HR 1000..1001 (배열 [hi, lo])\n"
            "  mold_pressure_sp -> HR 1002..1003 (배열 [hi, lo])"
        ),
        "x": 180, "y": 30, "wires": [],
    })

    # === Read 영역 (자체 폴링 1초) ===
    parse_coil = function_node(z, "parse power/alarm", PARSE_COIL, 540, 100)
    dbg_coil = debug_node(z, "power/alarm", 770, 100)
    parse_coil["wires"] = [[dbg_coil["id"]]]
    nodes.append(mb_read(z, "read coils [0..1]", "Coil", "0", "2",
                         x=320, y=100, out_wires=[parse_coil["id"]]))
    nodes.append(parse_coil); nodes.append(dbg_coil)

    parse_int = function_node(z, "parse int values", PARSE_INT, 560, 160)
    dbg_int = debug_node(z, "int values", 780, 160)
    parse_int["wires"] = [[dbg_int["id"]]]
    nodes.append(mb_read(z, "read HR [0..2] int", "HoldingRegister", "0", "3",
                         x=340, y=160, out_wires=[parse_int["id"]]))
    nodes.append(parse_int); nodes.append(dbg_int)

    parse_flt = function_node(z, "parse float (BE)", PARSE_FLOAT, 600, 220)
    dbg_flt = debug_node(z, "float values", 850, 220)
    parse_flt["wires"] = [[dbg_flt["id"]]]
    nodes.append(mb_read(z, "read HR [1000..1011] float", "HoldingRegister",
                         "1000", "12", x=360, y=220, out_wires=[parse_flt["id"]]))
    nodes.append(parse_flt); nodes.append(dbg_flt)

    # === Write 영역 ===
    # Power ON (coil 0 = true)
    w_pwr_on = mb_write(z, "write coil 0", "Coil", "0", "1", x=420, y=360)
    nodes.append(inject_node(z, "Power ON", "true", "bool",
                             x=140, y=360, wires=[w_pwr_on["id"]]))
    nodes.append(w_pwr_on)

    # Power OFF
    w_pwr_off = mb_write(z, "write coil 0", "Coil", "0", "1", x=420, y=410)
    nodes.append(inject_node(z, "Power OFF", "false", "bool",
                             x=140, y=410, wires=[w_pwr_off["id"]]))
    nodes.append(w_pwr_off)

    # motor_rpm_sp = 1500 (int, HR 0) — payload 스칼라
    w_rpm = mb_write(z, "write HR 0 int", "HoldingRegister", "0", "1", x=440, y=470)
    nodes.append(inject_node(z, "motor_rpm_sp = 1500", "1500", "num",
                             x=160, y=470, wires=[w_rpm["id"]]))
    nodes.append(w_rpm)

    # melt_temp_sp = 900.0 (float, HR 1000..1001) — function 으로 배열 변환
    w_melt = mb_write(z, "write HR 1000 (qty=2)", "HoldingRegister", "1000", "2",
                      x=680, y=530)
    fn_melt = function_node(z, "float -> [hi, lo]", FLOAT_TO_WORDS, 430, 530,
                            wires=[w_melt["id"]])
    nodes.append(inject_node(z, "melt_temp_sp = 900.0", "900.0", "num",
                             x=160, y=530, wires=[fn_melt["id"]]))
    nodes.append(fn_melt); nodes.append(w_melt)

    # mold_pressure_sp = 100.0 (float, HR 1002..1003)
    w_mold = mb_write(z, "write HR 1002 (qty=2)", "HoldingRegister", "1002", "2",
                      x=680, y=590)
    fn_mold = function_node(z, "float -> [hi, lo]", FLOAT_TO_WORDS, 430, 590,
                            wires=[w_mold["id"]])
    nodes.append(inject_node(z, "mold_pressure_sp = 100.0", "100.0", "num",
                             x=170, y=590, wires=[fn_mold["id"]]))
    nodes.append(fn_mold); nodes.append(w_mold)

    return nodes


# ---------------------------------------------------------------------------
# machining_01 (OPC UA) 탭
# ---------------------------------------------------------------------------

OPCUA_TAGS_ALL = [
    ("power", "Boolean"),
    ("spindle_temp_sp", "Float"),
    ("vibration_sp", "Float"),
    ("feed_rate_sp", "Float"),
    ("rpm_sp", "Int32"),
    ("spindle_temp", "Float"),
    ("vibration", "Float"),
    ("feed_rate", "Float"),
    ("rpm", "Int32"),
    ("part_count", "Int32"),
    ("alarm", "Boolean"),
]


def opcua_item(z: str, tag: str, dtype: str, x: int, y: int, wires: list) -> dict:
    return {
        "id": nid(), "type": "OpcUa-Item", "z": z,
        "item": f"ns=2;s={tag}",
        "datatype": dtype, "value": "",
        "name": tag,
        "x": x, "y": y, "wires": [wires],
    }


def opcua_client(z: str, action: str, name: str, x: int, y: int,
                 wires: list) -> dict:
    return {
        "id": nid(), "type": "OpcUa-Client", "z": z,
        "endpoint": OPCUA_CLIENT,
        "action": action,
        "deadbandtype": "a", "deadbandvalue": 1,
        "time": 10, "timeUnit": "s",
        "certificate": "n", "localfile": "", "localkeyfile": "",
        "securitymode": "None", "securitypolicy": "None",
        "useTransport": False, "maxChunkCount": "1", "maxMessageSize": "8192",
        "maxBufferSize": "8192", "receiveBufferSize": "8192",
        "sendBufferSize": "8192",
        "setstatusandtime": False, "keepsessionalive": False,
        "name": name,
        "x": x, "y": y, "wires": [wires],
    }


def build_machining_nodes() -> list[dict]:
    z = MACHINING_TAB
    nodes: list[dict] = []

    nodes.append({
        "id": nid(), "type": "comment", "z": z,
        "name": "machining_01 (OPC UA :4841)",
        "info": (
            "opc.tcp://127.0.0.1:4841/\n\n"
            "namespace index 가 ns=2 가 아닐 경우\n"
            "각 OpcUa-Item 의 item 값 (ns=<n>;s=<tag>) 을 실제 index 로 수정.\n\n"
            "RW: power, *_sp\n"
            "RO: spindle_temp/vibration/feed_rate/rpm/part_count/alarm\n"
            "RO 에 write 시 BadUserAccessDenied"
        ),
        "x": 200, "y": 30, "wires": [],
    })

    # === Read: 각 태그마다 item -> client(read) -> debug ===
    item_ids: list[str] = []
    y = 80
    for tag, dtype in OPCUA_TAGS_ALL:
        dbg = debug_node(z, tag, 800, y)
        cli = opcua_client(z, "read", f"read {tag}", 580, y, [dbg["id"]])
        item = opcua_item(z, tag, dtype, 360, y, [cli["id"]])
        nodes.extend([item, cli, dbg])
        item_ids.append(item["id"])
        y += 35

    # 폴링 inject — 모든 item 트리거
    poll = inject_node(z, "poll 1s", "", "date",
                       x=140, y=80, repeat="1", once=True, once_delay="1",
                       wires=item_ids)
    nodes.append(poll)

    # === Write 영역 ===
    def add_write(label: str, tag: str, dtype: str,
                  payload: str, ptype: str, yoff: int):
        cli = opcua_client(z, "write", f"write {tag}", 650, yoff, [])
        item = opcua_item(z, tag, dtype, 420, yoff, [cli["id"]])
        inj = inject_node(z, label, payload, ptype, 170, yoff, wires=[item["id"]])
        nodes.extend([inj, item, cli])

    ctrl_y = 600
    add_write("Power ON",            "power",          "Boolean", "true",  "bool", ctrl_y)
    add_write("Power OFF",           "power",          "Boolean", "false", "bool", ctrl_y + 40)
    add_write("rpm_sp = 1800",       "rpm_sp",         "Int32",   "1800",  "num",  ctrl_y + 90)
    add_write("spindle_temp_sp = 60.0", "spindle_temp_sp", "Float", "60.0",  "num", ctrl_y + 130)
    add_write("vibration_sp = 0.8",     "vibration_sp",    "Float", "0.8",   "num", ctrl_y + 170)
    add_write("feed_rate_sp = 200.0",   "feed_rate_sp",    "Float", "200.0", "num", ctrl_y + 210)

    return nodes


# ---------------------------------------------------------------------------
# machining_01_mc (MC Protocol 3E Binary) 탭
#
# node-red-contrib-mcprotocol 노드 사용.
#   - MC Protocol Connection: host=127.0.0.1 port=5081 protocol=TCP frame=3E plcType=Q
#   - MC Read: address 는 "M0" / "D6" / "DFLOAT0" / "DFLOAT0,3" 형식
#   - MC Write: data 는 msg.payload, address 는 노드 속성으로 고정
# ---------------------------------------------------------------------------

def make_mc_connection_config() -> dict:
    return {
        "id": MC_CONNECTION,
        "type": "MC Protocol Connection",
        "name": "machining_01_mc @ 127.0.0.1:5081",
        "host": "127.0.0.1",
        "port": "5081",
        "protocol": "TCP",
        "frame": "3E",
        "plcType": "Q",
        "ascii": False,
        "PLCStation": "",
        "PCStation": "",
        "PLCModuleNo": "",
        "network": "",
        "octalInputOutput": False,
        "timeout": "1000",
    }


def mc_read(z: str, name: str, address: str, x: int, y: int,
            wires: list, output_format: int = 0) -> dict:
    """MC Read 노드. address 는 고정 문자열.

    output_format: 0 = JSON Object, 1 = Array.
    """
    return {
        "id": nid(),
        "type": "MC Read",
        "z": z,
        "name": name,
        "topic": "",
        "connection": MC_CONNECTION,
        "address": address,
        "addressType": "str",
        "outputFormat": output_format,
        "errorHandling": "throw",
        "outputs": 1,
        "x": x, "y": y,
        "wires": [wires],
    }


def mc_write(z: str, name: str, address: str, x: int, y: int,
             wires: list | None = None) -> dict:
    """MC Write 노드. data 는 msg.payload 사용."""
    return {
        "id": nid(),
        "type": "MC Write",
        "z": z,
        "name": name,
        "topic": "",
        "connection": MC_CONNECTION,
        "data": "payload",
        "dataType": "msg",
        "address": address,
        "addressType": "str",
        "errorHandling": "throw",
        "outputs": 1,
        "x": x, "y": y,
        "wires": [wires or []],
    }


def build_mc_nodes() -> list[dict]:
    z = MC_TAB
    nodes: list[dict] = []

    nodes.append({
        "id": nid(), "type": "comment", "z": z,
        "name": "machining_01_mc (MC Protocol 3E Binary :5081)",
        "info": (
            "127.0.0.1:5081, frame=3E, plcType=Q, binary\n\n"
            "[디바이스 매핑]\n"
            "  M0    power        bool (RW)\n"
            "  M10   alarm        bool (RO)\n"
            "  D0-1  spindle_temp_sp float (RW)\n"
            "  D2-3  vibration_sp    float (RW)\n"
            "  D4-5  feed_rate_sp    float (RW)\n"
            "  D6    rpm_sp          int   (RW)\n"
            "  D100-1 spindle_temp  float (RO)\n"
            "  D102-3 vibration     float (RO)\n"
            "  D104-5 feed_rate     float (RO)\n"
            "  D106   rpm           int   (RO)\n"
            "  D200   part_count    int   (RO)\n\n"
            "[주소 표기]\n"
            "  bit : M0 / M10\n"
            "  int : D6 / D106 / D200\n"
            "  float: DFLOAT0 / DFLOAT100 (2 word, low-word first)\n\n"
            "[RO write]\n"
            "  D100 등 RO 태그에 쓰면 EndCode 0xC0B4 -> MC Write 에서 에러"
        ),
        "x": 240, "y": 30, "wires": [],
    })

    # === Read 영역 ===
    # 폴링 inject (1초)
    read_addresses = [
        ("power (M0)",          "M0"),
        ("alarm (M10)",         "M10"),
        ("rpm_sp (D6)",         "D6"),
        ("rpm (D106)",          "D106"),
        ("part_count (D200)",   "D200"),
        ("spindle_temp_sp (DFLOAT0)",  "DFLOAT0"),
        ("vibration_sp (DFLOAT2)",     "DFLOAT2"),
        ("feed_rate_sp (DFLOAT4)",     "DFLOAT4"),
        ("spindle_temp (DFLOAT100)",   "DFLOAT100"),
        ("vibration (DFLOAT102)",      "DFLOAT102"),
        ("feed_rate (DFLOAT104)",      "DFLOAT104"),
    ]

    read_node_ids: list[str] = []
    y = 80
    for label, addr in read_addresses:
        dbg = debug_node(z, label, 800, y)
        rd = mc_read(z, label, addr, 500, y, wires=[dbg["id"]])
        nodes.append(rd); nodes.append(dbg)
        read_node_ids.append(rd["id"])
        y += 40

    nodes.append(inject_node(z, "poll 1s", "", "date",
                             x=160, y=80, repeat="1", once=True, once_delay="1",
                             wires=read_node_ids))

    # === Write 영역 ===
    write_y = y + 40

    def add_write(label: str, address: str, payload: str, ptype: str, yoff: int):
        w = mc_write(z, f"write {address}", address, 600, yoff)
        nodes.append(inject_node(z, label, payload, ptype,
                                 x=170, y=yoff, wires=[w["id"]]))
        nodes.append(w)

    add_write("Power ON  -> M0=true",   "M0",       "true",  "bool", write_y)
    add_write("Power OFF -> M0=false",  "M0",       "false", "bool", write_y + 40)
    add_write("rpm_sp = 1800",          "D6",       "1800",  "num",  write_y + 90)
    add_write("spindle_temp_sp = 72.5", "DFLOAT0",  "72.5",  "num",  write_y + 130)
    add_write("vibration_sp = 0.8",     "DFLOAT2",  "0.8",   "num",  write_y + 170)
    add_write("feed_rate_sp = 220.0",   "DFLOAT4",  "220.0", "num",  write_y + 210)

    # RO 거부 동작 확인용 (실패 메시지가 catch 노드 없이도 디버그 사이드바에 뜸)
    add_write("[RO test] write D100 (denied)", "D100", "0", "num", write_y + 270)

    return nodes


def build() -> list[dict]:
    flow: list[dict] = []
    flow.append({
        "id": CASTING_TAB, "type": "tab",
        "label": "casting_01 (Modbus)", "disabled": False, "info": "",
    })
    flow.append({
        "id": MACHINING_TAB, "type": "tab",
        "label": "machining_01 (OPC UA)", "disabled": False, "info": "",
    })
    flow.append({
        "id": MC_TAB, "type": "tab",
        "label": "machining_01_mc (MC Protocol)", "disabled": False, "info": "",
    })
    flow.append(make_modbus_server_config())
    flow.append(make_opcua_client_config())
    flow.append(make_mc_connection_config())
    flow.extend(build_casting_nodes())
    flow.extend(build_machining_nodes())
    flow.extend(build_mc_nodes())
    return flow


if __name__ == "__main__":
    out_path = Path(__file__).parent / "flows_control_test.json"
    data = build()
    out_path.write_text(json.dumps(data, indent=2, ensure_ascii=False),
                        encoding="utf-8")
    print(f"wrote {out_path} ({len(data)} nodes)")
