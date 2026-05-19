"""라인 DAS Node-RED flow 빌더 (docker 1라인 모델).

라인 1개 = 9개 설비 + Node-RED 1개 (docker-compose 1 묶음).
1초 주기로 폴링 -> docs/integration_spec.md §5 규격대로 통합 JSON 페이로드 발행
-> OPC UA Server (포트 4870) 의 ns=2;s=<LINE_ID>.payload 변수로 expose.

설계 원칙 (사용자 요구):
- 과설계 금지: 라인 1개 = 탭 1개
- 클라이언트 3종 (Modbus / MC / OPC UA) 사용. defaults 키만.
- aggregate 는 단일 function 노드에서 flow context 모음, 매 1초마다 페이로드 발행
- OPC UA Server (4870) 는 1개 변수에 JSON 문자열로 expose (단순/안정)

사용법:
  # docker-compose 안에서 (다른 컨테이너 host = service 이름)
  LINE_ID=LINE-01 python nodered/build_flow_das.py --host-mode docker

  # 호스트에서 직접 (localhost 포트 매핑)
  LINE_ID=LINE-01 python nodered/build_flow_das.py --host-mode localhost

빌더 자체는 configs/_generate.py 의 매핑/포트를 그대로 import 해서
태그 / Modbus 매핑 / MC 매핑이 시뮬레이터와 어긋나지 않게 한다.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from configs import _generate as gen  # type: ignore


# ---------------------------------------------------------------------------
# 포트 / 호스트 (라인별 +100)
# ---------------------------------------------------------------------------

import re as _re

# Node-RED 라인 DAS OPC UA Server 포트 (LINE-01: 4860, LINE-02: 4960, LINE-03: 5060)
DAS_OPCUA_BASE = 4860
PORT_STRIDE = 100


def line_number(line_id: str) -> int:
    """LINE-01 -> 1, LINE-02 -> 2, ..."""
    m = _re.match(r"LINE-(\d+)", line_id)
    if not m:
        raise ValueError(f"invalid LINE_ID: {line_id!r} (expected 'LINE-NN')")
    n = int(m.group(1))
    if n < 1:
        raise ValueError(f"line number must be >= 1, got {n}")
    return n


def line_port(base_port: int, line_id: str) -> int:
    return base_port + (line_number(line_id) - 1) * PORT_STRIDE


def das_opcua_port(line_id: str) -> int:
    return DAS_OPCUA_BASE + (line_number(line_id) - 1) * PORT_STRIDE


def host_for(eq_id: str, host_mode: str) -> str:
    """host_mode 에 따라 설비 IP/호스트 반환.

    docker      -> docker-compose service 이름 (소문자, e.g. 'cast-01')
    localhost   -> '127.0.0.1' (compose 가 호스트 포트로 노출했을 때)

    CNC-* 는 포트가 아닌 시리얼이므로 이 함수 결과가 필요 없음 — 호출측이 알아서 넘글
    """
    if host_mode == "docker":
        return eq_id.lower()
    if host_mode == "localhost":
        return "127.0.0.1"
    raise ValueError(f"unknown host_mode: {host_mode}")


# ---------------------------------------------------------------------------
# Modbus 매핑 재계산 (sim/protocols/modbus_server.py build_mapping 과 동일 규칙)
#   - bool  -> coil[순번]
#   - int   -> HR[순번]                       (16bit signed)
#   - float -> HR[FLOAT_BASE=1000 + 순번*2]   (big-endian, 2 word)
# ---------------------------------------------------------------------------

def build_modbus_mapping(tags: list[dict]) -> dict[str, tuple[str, int]]:
    """config 에 박힌 mb 매핑을 그대로 쓰기.

    {name -> (kind, address)}, kind ∈ {'coil', 'hr_int', 'hr_float'}.
    """
    mapping: dict[str, tuple[str, int]] = {}
    for t in tags:
        mb = t.get("mb")
        if mb is None:
            raise ValueError(
                f"tag '{t['name']}' missing 'mb' 명세 — configs/_generate.py 갱신 필요"
            )
        mapping[t["name"]] = (mb["kind"], mb["address"])
    return mapping


# ---------------------------------------------------------------------------
# 노드 ID 생성
# ---------------------------------------------------------------------------

def nid() -> str:
    return uuid.uuid4().hex[:16]


# ---------------------------------------------------------------------------
# 공통 노드 빌더
# ---------------------------------------------------------------------------

def inject_node(z: str, name: str, payload: str, ptype: str,
                x: int, y: int, *,
                repeat: str = "", once: bool = True, once_delay: str = "2",
                wires: list | None = None) -> dict:
    return {
        "id": nid(), "type": "inject", "z": z, "name": name,
        "props": [{"p": "payload"}],
        "repeat": repeat, "crontab": "",
        "once": once, "onceDelay": once_delay,
        "topic": "", "payload": payload, "payloadType": ptype,
        "x": x, "y": y, "wires": [wires or []],
    }


def function_node(z: str, name: str, code: str, x: int, y: int,
                  outputs: int = 1, wires: list | None = None) -> dict:
    """function 노드 생성.

    wires 는 항상 Node-RED 원형 형식: outputs 개의 list-of-ids.
    예: outputs=1 이면 [["id1", "id2"]] (1개 출력 포트에 2개 연결)
    예: outputs=2 이면 [["id1"], ["id2"]]
    """
    if wires is None:
        wires = [[] for _ in range(outputs)]
    if outputs == 0:
        wires = []
    return {
        "id": nid(), "type": "function", "z": z, "name": name,
        "func": code,
        "outputs": outputs, "noerr": 0,
        "initialize": "", "finalize": "", "libs": [],
        "x": x, "y": y,
        "wires": wires,
    }


def debug_node(z: str, name: str, x: int, y: int, *,
               to_console: bool = False, complete: str = "payload") -> dict:
    return {
        "id": nid(), "type": "debug", "z": z, "name": name,
        "active": True, "tosidebar": True,
        "console": to_console, "tostatus": False,
        "complete": complete, "targetType": "msg",
        "statusVal": "", "statusType": "auto",
        "x": x, "y": y, "wires": [],
    }


def comment_node(z: str, name: str, info: str, x: int, y: int) -> dict:
    return {
        "id": nid(), "type": "comment", "z": z, "name": name,
        "info": info, "x": x, "y": y, "wires": [],
    }


# ---------------------------------------------------------------------------
# Modbus 노드 빌더
# ---------------------------------------------------------------------------

def make_modbus_client_config(server_id: str, host: str, port: int,
                              equipment_name: str,
                              *,
                              transport: str = "tcp",
                              tcp_type: str = "DEFAULT",
                              serial_port: str = "") -> dict:
    """node-red-contrib-modbus 의 modbus-client config.

    transport='tcp', tcp_type='DEFAULT'             : 순수 Modbus-TCP (WASH-01)
    transport='tcp', tcp_type='TCP-RTU-BUFFERED'    : RTU framing on TCP
                                                       (CNC — Moxa NPort 방식,
                                                        시뮬이 직접 TCP listen)
    transport='simpleser', serial_port=...          : 순수 RS-485 시리얼 (현재 미사용)

    ⚠️ contrib-modbus 는 RTU-over-TCP 도 clienttype='tcp' 으로 둔다.
       절대 'tcp-rtu-buffered' 같은 값을 쓰지 말 것 — modbus-client.js 가
       node.clienttype === 'tcp' 일 때만 tcpType switch 를 타다.
    """
    # required 필드 기본값 — transport=tcp 이라도 serial* 는 valid 값으로 채워야 함.
    # 안 그러면 modbus-read 측에서 "유효하지 않은 속성: server" 경고.
    sp = serial_port if serial_port else "/dev/ttyUSB0"
    return {
        "id": server_id, "type": "modbus-client",
        "name": f"{equipment_name} @ {host}:{port}" if transport == "tcp" else f"{equipment_name} @ {sp}",
        "clienttype": transport,
        "bufferCommands": True,
        "stateLogEnabled": False, "queueLogEnabled": False, "failureLogEnabled": True,
        "tcpHost": host or "127.0.0.1", "tcpPort": str(port or 502), "tcpType": tcp_type,
        "serialPort": sp, "serialType": "RTU-BUFFERD",
        "serialBaudrate": "9600", "serialDatabits": "8",
        "serialStopbits": "1", "serialParity": "none",
        "serialConnectionDelay": "100", "serialAsciiResponseStartDelimiter": "0x3A",
        "unit_id": "1", "commandDelay": "1",
        "clientTimeout": "2000",
        "reconnectOnTimeout": True, "reconnectTimeout": "2000",
        "parallelUnitIdsAllowed": True,
        "showErrors": False, "showWarnings": True, "showLogs": False,
    }


def mb_read(z: str, server_id: str, name: str, dtype: str, adr: str, qty: str,
            rate: str = "1", rate_unit: str = "s",
            x: int = 200, y: int = 100,
            out_wires: list | None = None) -> dict:
    return {
        "id": nid(), "type": "modbus-read", "z": z, "name": name,
        "topic": "",
        "showStatusActivities": False, "logIOActivities": False,
        "showErrors": False, "showWarnings": True,
        "unitid": "",
        "dataType": dtype, "adr": adr, "quantity": qty,
        "rate": rate, "rateUnit": rate_unit,
        "delayOnStart": True, "startDelayTime": "2",
        "server": server_id,
        "useIOFile": False, "ioFile": "",
        "useIOForPayload": False, "emptyMsgOnFail": False,
        "x": x, "y": y,
        "wires": [out_wires or [], []],
    }


# ---------------------------------------------------------------------------
# MC Protocol 노드 빌더
# ---------------------------------------------------------------------------

def make_mc_connection_config(conn_id: str, host: str, port: int,
                              equipment_name: str) -> dict:
    return {
        "id": conn_id, "type": "MC Protocol Connection",
        "name": f"{equipment_name} @ {host}:{port}",
        "host": host, "port": str(port),
        "protocol": "TCP", "frame": "3E", "plcType": "Q",
        "ascii": False,
        "PLCStation": "", "PCStation": "", "PLCModuleNo": "", "network": "",
        "octalInputOutput": False,
        "timeout": "2000",
    }


def mc_read(z: str, conn_id: str, name: str, address: str,
            x: int, y: int, wires: list,
            output_format: int = 0) -> dict:
    """MC Read 노드. outputFormat 0=JSON, 1=Array."""
    return {
        "id": nid(), "type": "MC Read", "z": z, "name": name,
        "topic": "",
        "connection": conn_id,
        "address": address, "addressType": "str",
        "outputFormat": output_format,
        "errorHandling": "throw",
        "outputs": 1,
        "x": x, "y": y,
        "wires": [wires],
    }


# ---------------------------------------------------------------------------
# OPC UA 노드 빌더
# ---------------------------------------------------------------------------

def make_opcua_endpoint_config(endpoint_id: str, host: str, port: int) -> dict:
    return {
        "id": endpoint_id, "type": "OpcUa-Endpoint",
        "endpoint": f"opc.tcp://{host}:{port}/",
        "secpol": "None", "secmode": "None",
        "none": True, "login": False,
        "usercert": False, "usercertificate": "", "userprivatekey": "",
    }


def opcua_item(z: str, tag: str, dtype: str,
               x: int, y: int, wires: list) -> dict:
    return {
        "id": nid(), "type": "OpcUa-Item", "z": z,
        "item": f"ns=2;s={tag}",
        "datatype": dtype, "value": "",
        "name": tag,
        "x": x, "y": y, "wires": [wires],
    }


def opcua_client(z: str, endpoint_id: str, action: str, name: str,
                 x: int, y: int, wires: list) -> dict:
    return {
        "id": nid(), "type": "OpcUa-Client", "z": z,
        "endpoint": endpoint_id,
        "action": action,
        "deadbandtype": "a", "deadbandvalue": 1,
        "time": 10, "timeUnit": "s",
        "certificate": "n", "localfile": "", "localkeyfile": "",
        "securitymode": "None", "securitypolicy": "None",
        "useTransport": False,
        "maxChunkCount": "1", "maxMessageSize": "8192",
        "maxBufferSize": "8192", "receiveBufferSize": "8192",
        "sendBufferSize": "8192",
        "setstatusandtime": False, "keepsessionalive": False,
        "name": name,
        "x": x, "y": y, "wires": [wires],
    }


# ---------------------------------------------------------------------------
# OPC UA tag dtype 매핑
# ---------------------------------------------------------------------------

def opcua_dtype(data_type: str) -> str:
    return {"bool": "Boolean", "int": "Int32", "float": "Float"}[data_type]


# ---------------------------------------------------------------------------
# Write 노드 빌더 (SP Control 탭에서 사용)
# ---------------------------------------------------------------------------

def mb_write(z: str, server_id: str, name: str, dtype: str, adr: str, qty: str,
             x: int, y: int, out_wires: list | None = None) -> dict:
    """modbus-write 노드. msg.payload 값(또는 array)을 dataType/adr 위치에 기록.

    contrib-modbus 의 modbus-write 는 modbus-read 와 동일한 클라이언트 config 를 공유한다.
    dtype ∈ {'Coil', 'HoldingRegister', ...}.
    float (BE, 2 word) 를 쓸 때는 msg.payload 가 길이 2 의 uint16 array 여야 한다.
    """
    return {
        "id": nid(), "type": "modbus-write", "z": z, "name": name,
        "showStatusActivities": False,
        "showErrors": False, "showWarnings": True,
        "unitid": "",
        "dataType": dtype, "adr": adr, "quantity": qty,
        "emptyMsgOnFail": False, "keepMsgProperties": False,
        "delayOnStart": False, "startDelayTime": "",
        "server": server_id,
        "x": x, "y": y,
        "wires": [out_wires or [], []],
    }


def mc_write(z: str, conn_id: str, name: str, address: str,
             x: int, y: int, out_wires: list | None = None) -> dict:
    """MC Write 노드. data='payload', dataType='msg' 로 두면 msg.payload 값을 기록.

    addressType='str' + address='D0' / 'DFLOAT0' / 'M100' 형식.
    """
    return {
        "id": nid(), "type": "MC Write", "z": z, "name": name,
        "topic": "",
        "connection": conn_id,
        "address": address, "addressType": "str",
        "data": "payload", "dataType": "msg",
        "errorHandling": "throw",
        "outputs": 1,
        "x": x, "y": y,
        "wires": [out_wires or []],
    }


def opcua_write_prep(z: str, tag: str, dtype: str,
                    x: int, y: int, wires: list) -> dict:
    """OpcUa-Client write 용 msg 구성 function 노드.

    msg.topic = ns=2;s=<tag>, msg.datatype = Float/Int32/Boolean.
    msg.payload 는 inject 가 넣어준 값 그대로 통과.
    """
    code = (
        "// OpcUa-Client write 용 msg 구성\n"
        f"msg.topic = {f'ns=2;s={tag}'!r};\n"
        f"msg.datatype = {dtype!r};\n"
        "return msg;\n"
    )
    return function_node(z, f"prep write {tag}", code, x=x, y=y,
                         outputs=1, wires=[wires])


# ---------------------------------------------------------------------------
# 페이로드 aggregate / emit / publish 코드
# ---------------------------------------------------------------------------

AGGREGATE_TAGS_CODE = """\
// 각 설비 reader 가 보낸 부분 dict 를 flow context 에 누적.
// parser 는 msg.payload = {equipment_id, tags} 형태로 전달한다.
const p = msg.payload || {};
const eq = p.equipment_id;
if (!eq) return null;
const buf = flow.get('equipments') || {};
const prev = buf[eq] || { data: {} };
prev.data = Object.assign(prev.data || {}, p.tags || {});
prev.last_update_ms = Date.now();
buf[eq] = prev;
flow.set('equipments', buf);
return null;
"""

# 라인 페이로드 발행: 1초마다 호출
#
# status 판정은 명세 §5 을 따르되, 시모 태그에서 alarm 이 제거되었고
# WARNING/DANGER 론리는 향후 확장으로 남겨둔다.
# 현 단계 구현은 단순하게:
#   data 가 비었거나 폴링 실패 -> OFF + BAD
#   power == false                                 -> OFF
#   power == true                                   -> RUN
# cycle_time 싀탼서는 progress 를 누적, 1.0 도달 시 cycle_time 갱신
EMIT_PAYLOAD_CODE_TMPL = """\
// 1초마다 라인 페이로드 발행 + cycle_time 산출
const LINE_ID = "%(line_id)s";
const SCHEMA_VERSION = "1.0";
const EQUIPMENTS = %(equipments_json)s;
// 설비별 기대 tag 맵: { eq_id: { tag_name: data_type } }
// 아직 읽히지 않은 값은 data_type 기본값으로 채워 서
// 통합 DAS 가 OPC UA 변수 누락 에러를 내지 않도록 보장.
const EXPECTED_TAGS = %(expected_tags_json)s;
// 설비별 nominal cycle_sec (첫 사이클 완료 전 cycle_time 기본값 및 표시용)
const NOMINAL_CYCLE = %(nominal_cycle_json)s;
function defaultForType(t) {
  if (t === 'bool') return false;
  if (t === 'int') return 0;
  if (t === 'float') return 0.0;
  return '';
}
const STALE_MS = 3000;  // 3초 이상 미갱신 -> UNCERTAIN

const buf = flow.get('equipments') || {};
const cyc = flow.get('cycle_state') || {};  // { eq: { acc, last_cycle_time } }
const now = Date.now();
const nowIso = new Date(now).toISOString();

function classify(data, last_update_ms) {
  if (!data || Object.keys(data).length === 0) {
    return { status: 'OFF', quality: 'BAD' };
  }
  // status (시모에서는 OFF / RUN 두 개만 생성. WARNING/DANGER 는 향후 확장)
  const status = data.power === false ? 'OFF' : 'RUN';
  // quality
  let quality;
  if (!last_update_ms) quality = 'BAD';
  else if (now - last_update_ms > STALE_MS) quality = 'UNCERTAIN';
  else quality = 'GOOD';
  return { status, quality };
}

const equipments = {};
for (const eq_id of EQUIPMENTS) {
  const slot = buf[eq_id] || {};
  const data = Object.assign({}, slot.data || {});
  // 기대 tag 중 아직 안 온 것은 data_type 기본값으로 미리 박아서
  // PUBLISH 가 제대로 된 dtype 으로 addVariable 하게 함.
  const expected = EXPECTED_TAGS[eq_id] || {};
  for (const [tagName, tagType] of Object.entries(expected)) {
    if (!(tagName in data)) data[tagName] = defaultForType(tagType);
  }
  const { status, quality } = classify(data, slot.last_update_ms);

  // ----- cycle_time 산출 -----
  // 한 사이클(progress 누적이 1.0 도달) 이 끝난 시점의 경과시간을 측정해
  // "마지막으로 완료된 사이클의 cycle_time" 으로 저장하고,
  // 다음 사이클이 끝날 때까지 매 tick 같은 값을 반환 (hold).
  // 첫 사이클이 끝나기 전까지는 설비 명세의 cycle_sec(60/180/120)을 기본값으로.
  const NOMINAL = (NOMINAL_CYCLE[eq_id] || 60);
  let st = cyc[eq_id] || {
    acc: 0.0,
    last_cycle_time: NOMINAL,   // 부팅 직후 기본값
    start_ms: now,
  };
  if (status === 'RUN' && typeof data.progress === 'number') {
    st.acc += data.progress;
    if (st.acc >= 1.0) {
      const dt = (now - (st.start_ms || now)) / 1000.0;
      st.last_cycle_time = +dt.toFixed(2);   // 다음 100%% 도달 전까지 hold
      st.acc = 0.0;
      st.start_ms = now;
    }
  }
  // OFF 일 때는 acc / start_ms 유지 (다음 RUN 에서 이어 계산).
  cyc[eq_id] = st;
  data.cycle_time = st.last_cycle_time;  // 항상 hold 된 값을 전달

  equipments[eq_id] = {
    status: status,
    ts: slot.last_update_ms ? new Date(slot.last_update_ms).toISOString() : nowIso,
    quality: quality,
    data: data,
  };
}

flow.set('cycle_state', cyc);

const payload = {
  ts: nowIso,
  line_id: LINE_ID,
  schema_version: SCHEMA_VERSION,
  equipments: equipments,
};

return { payload: payload };
"""


MODBUS_PARSE_TMPL = """\
// Modbus read 결과를 평탄 dict 로 묶어 aggregator 로 보냄.
// payload 는 read 노드의 dataType 에 따라 배열(coil/HR).
const KIND = "%(kind)s";       // 'coil' | 'hr_int' | 'hr_float'
const NAMES = %(names_json)s;  // 이 read 노드가 커버하는 tag name 순서
const ADR_BASE = %(adr_base)d;
const r = msg.payload || msg.responseBuffer && msg.responseBuffer.data || [];
const tags = {};
if (KIND === 'coil') {
  NAMES.forEach((n, i) => { tags[n] = !!r[i]; });
} else if (KIND === 'hr_int') {
  function s16(v){ v &= 0xFFFF; return v & 0x8000 ? v - 0x10000 : v; }
  NAMES.forEach((n, i) => { tags[n] = s16(r[i]); });
} else if (KIND === 'hr_float') {
  // r 은 2*N word 배열 (big-endian)
  const buf = Buffer.alloc(NAMES.length * 4);
  for (let i = 0; i < NAMES.length * 2; i++) {
    buf.writeUInt16BE((r[i] | 0) & 0xFFFF, i * 2);
  }
  NAMES.forEach((n, i) => {
    tags[n] = +buf.readFloatBE(i * 4).toFixed(3);
  });
}
// msg.payload 에 담아 반환 → aggregator + debug 둘 다 그대로 읽을 수 있음
msg.payload = { equipment_id: "%(equipment_id)s", tags: tags };
return msg;
"""


MC_PARSE_TMPL = """\
// MC Read 결과 -> 평탄 dict
// addressType=str, outputFormat=0(JSON object)인 경우 msg.payload 는
// 단일값(스칼라/bool) 또는 객체일 수 있다. mcprotocol contrib 동작:
//   - 단일 디바이스(M0, D0)   : 스칼라 또는 길이1 배열
//   - DFLOAT0,N (N>=2)        : 배열
//   - 단일 DFLOAT0            : 스칼라 float
const NAMES = %(names_json)s;       // 이 read 가 커버하는 tag name 순서
const TYPE  = "%(type)s";           // 'bit' | 'int' | 'float'
const p = msg.payload;
const arr = Array.isArray(p) ? p : [p];
const tags = {};
if (TYPE === 'bit') {
  NAMES.forEach((n, i) => { tags[n] = !!arr[i]; });
} else if (TYPE === 'int') {
  NAMES.forEach((n, i) => { tags[n] = (arr[i] | 0); });
} else if (TYPE === 'float') {
  NAMES.forEach((n, i) => { tags[n] = +Number(arr[i]).toFixed(3); });
}
msg.payload = { equipment_id: "%(equipment_id)s", tags: tags };
return msg;
"""


OPCUA_COLLECT_TMPL = """\
// OPC UA Item read 결과를 누적 후 aggregator 로 일괄 전송
// 각 read 결과는 msg.payload 에 단일값, msg.topic 에 NodeId.
const EQ = "%(equipment_id)s";
const TAG = msg.opcuaItemName || msg.topic || msg.browseName || "";
// item name 은 NodeId 의 s=... 부분
let name = String(TAG);
const m = name.match(/s=([^;]+)/);
if (m) name = m[1];

function unwrapValue(payload) {
  if (payload && typeof payload === 'object') {
    if (payload.value && typeof payload.value === 'object' && 'value' in payload.value) {
      return payload.value.value;
    }
    if ('value' in payload && typeof payload.value !== 'object') {
      return payload.value;
    }
    if ('payload' in payload) {
      return unwrapValue(payload.payload);
    }
  }
  return payload;
}

const buf = flow.get('opcua_buf_%(equipment_id)s') || {};
buf[name] = unwrapValue(msg.payload);
flow.set('opcua_buf_%(equipment_id)s', buf);
return null;
"""

OPCUA_FLUSH_TMPL = """\
// 1초 trigger: 누적된 OPC UA 값 한꺼번에 발사
const EQ = "%(equipment_id)s";
const NAMES = %(names_json)s;
const buf = flow.get('opcua_buf_' + EQ) || {};
const tags = {};
for (const n of NAMES) {
  if (n in buf) tags[n] = buf[n];
}
if (Object.keys(tags).length === 0) return null;
msg.payload = { equipment_id: EQ, tags: tags };
return msg;
"""


# ---------------------------------------------------------------------------
# OPC UA Server 노드 (라인 페이로드 1개 JSON 변수)
# ---------------------------------------------------------------------------

def make_opcua_server_node(z: str, line_id: str, port: int,
                           x: int, y: int) -> dict:
    """node-red-contrib-opcua 의 OpcUa-Server 노드.

    들어오는 msg 의 topic = NodeId, payload = 값.
    """
    return {
        "id": nid(), "type": "OpcUa-Server", "z": z,
        "port": port,
        "endpoint": f"line-das/{line_id}",
        "acceptExternalCommands": True,
        "maxNodesPerBrowse": 0, "maxNodesPerHistoryReadData": 0,
        "maxNodesPerHistoryReadEvents": 0, "maxNodesPerWrite": 0,
        "maxNodesPerMethodCall": 0, "maxNodesPerRegisterNodes": 0,
        "maxNodesPerNodeManagement": 0, "maxMonitoredItemsPerCall": 0,
        "maxNodesPerHistoryUpdateData": 0, "maxNodesPerHistoryUpdateEvents": 0,
        "maxNodesPerRead": 0, "maxNodesPerTranslateBrowsePathsToNodeIds": 0,
        "name": f"DAS Server {line_id} :{port}",
        "usersFile": "",
        "nodesetDir": "",
        "autoAcceptUnknownCertificate": True,
        "registerToDiscovery": False,
        "constructDefaultAddressSpace": True,
        "allowAnonymous": True,
        "endpointNone": True,
        "endpointSign": False,
        "endpointSignEncrypt": False,
        "endpointBasic128Rsa15": False,
        "endpointBasic256": False,
        "endpointBasic256Sha256": False,
        "isAuditing": False,
        "serverDiscovery": True,
        "maxConnectionsPerEndpoint": 20,
        "maxMessageSize": "10485760",
        "maxBufferSize": "10485760",
        "maxSessions": 20,
        "users": [],
        "xmlsetname": "", "xmlsetname2": "",
        "x": x, "y": y, "wires": [[]],
    }


PUBLISH_PAYLOAD_CODE_TMPL = """\
// payload 를 OPC UA Server 변수로 발행
//
// node-red-contrib-opcua 의 OpcUa-Server 는 2단계 프로토콜:
//   1) addVariable 명령으로 주소공간에 노드 등록
//      msg.topic   = 'ns=2;s=<name>;datatype=<DT>'
//      msg.payload = { opcuaCommand: 'addVariable' }
//   2) 이후 Variable 명령으로 값 갱신
//      msg.payload = { messageType:'Variable', namespace:2,
//                      variableName:<name>, variableValue:<val>, datatype:<DT> }
//
// addVariable 과 Variable 을 같은 tick 의 연속된 msg 로 보내면
// OpcUa-Server 가 주소공간 등록을 끝내기 전에 Variable 갱신이 들어와서
// "Variable not found" 에러가 다수 발생한다.
//
// 근본 해결책 (race 완전 제거):
//   1) 변수별 상태를 3단계로 관리: 'new' -> 'pending' -> 'ready'
//      - 처음 만난 변수는 addVariable 만 보내고 'pending' 으로 표시
//      - 다음 tick 부터 'pending' 인 변수는 Variable update SKIP 하고 'ready' 로 승격
//      - 'ready' 부터 실제 Variable update 전송
//   2) 즉 addVariable 보낸 tick (1초 간격) + 2 tick 뒤부터 update 시작
//      → OpcUa-Server 가 등록을 끝낼 약 1초 이상 확보
//   3) Variable update 는 contrib-opcua array payload 형식으로 한번에 묶어 전송
const EXPECTED_TYPES = %(expected_tags_json)s;
const p = msg.payload;
if (!p) return null;
const lineId = p.line_id;
// state['name'] = 'pending' | 'ready'
const state = flow.get('opcua_state') || {};
const addMsgs = [];
const varList = [];

function coerce(value, dtype) {
  if (value === null || value === undefined) {
    if (dtype === 'Boolean') return false;
    if (dtype === 'Int32') return 0;
    if (dtype === 'Double' || dtype === 'Float') return 0.0;
    return '';
  }
  return value;
}

function publish(name, value, dtype) {
  const v = coerce(value, dtype);
  const s = state[name];
  if (!s) {
    // 첫 발견: addVariable 만 보냄
    addMsgs.push({
      topic: `ns=2;s=${name};datatype=${dtype}`,
      payload: { opcuaCommand: 'addVariable' },
    });
    state[name] = 'pending';
    return;
  }
  if (s === 'pending') {
    // 한 tick 더 기다림 -> ready 로 승격, 이번 tick 도 update skip
    state[name] = 'ready';
    return;
  }
  // ready: 실제 update
  varList.push({
    messageType: 'Variable',
    namespace: 2,
    variableName: name,
    variableValue: v,
    datatype: dtype,
  });
}

publish(`${lineId}.payload`,         JSON.stringify(p),     'String');
publish(`${lineId}.line_ts`,         p.ts,                   'String');
publish(`${lineId}.schema_version`,  p.schema_version,       'String');
for (const [eq, slot] of Object.entries(p.equipments || {})) {
  publish(`${lineId}.${eq}.status`,  slot.status,            'String');
  publish(`${lineId}.${eq}.ts`,      slot.ts,                'String');
  publish(`${lineId}.${eq}.quality`, slot.quality,           'String');
  const typeMap = (EXPECTED_TYPES[eq] || {});
  for (const [tag, val] of Object.entries(slot.data || {})) {
    let dtype;
    const declared = typeMap[tag];
    if (declared === 'bool') dtype = 'Boolean';
    else if (declared === 'int') dtype = 'Int32';
    else if (declared === 'float') dtype = 'Double';
    else {
      // 알려지지 않은 동적 tag (cycle_time, progress 등) — 무조건 Double 로 등록.
      if (typeof val === 'boolean') dtype = 'Boolean';
      else if (typeof val === 'number') dtype = 'Double';
      else dtype = 'String';
    }
    publish(`${lineId}.${eq}.data.${tag}`, val, dtype);
  }
}
flow.set('opcua_state', state);

// 단계 1: addVariable msg 들 즉시 전송 (array-of-messages 출력 형식)
if (addMsgs.length > 0) {
  node.send([addMsgs]);
}

// 단계 2: Variable update 는 array payload 로 한번에 전송 (state='ready' 인 것만)
//   addMsgs 가 있는 tick (새 변수 발견된 tick) 은 800ms 지연으로 안전 마진,
//   안정 상태에선 50ms 정도 지연으로 send 순서만 보장.
if (varList.length > 0) {
  const delay = (addMsgs.length > 0) ? 800 : 50;
  setTimeout(() => {
    node.send({ payload: varList });
  }, delay);
}

return null;
"""


# ---------------------------------------------------------------------------
# 통합 DAS 용 압축 alias
# ---------------------------------------------------------------------------
# integration/build_flow_integration.py 가 import 하는 구포롌 이름.
# 라인 ID 는 동적(`p.line_id`)이므로 eq_id 기준으로 expected_types map 을
# 하나만 만들어 두면 3라인 모두 동일하게 쓰일 수 있다.
def _build_expected_types_for_integration() -> dict:
    et: dict = {}
    for sp in gen.EQUIPMENT_SPECS:
        eq, proto, _bp, fn, csec, _ss = sp
        tag_list = gen.build_tags(fn(), proto, csec, eq)
        et[eq] = {t["name"]: t["data_type"] for t in tag_list}
    return et


PUBLISH_PAYLOAD_CODE = PUBLISH_PAYLOAD_CODE_TMPL % {
    "expected_tags_json": json.dumps(_build_expected_types_for_integration()),
}


# ---------------------------------------------------------------------------
# 메인 빌더
# ---------------------------------------------------------------------------

def build_line_flow(line_id: str, host_mode: str) -> list[dict]:
    tab_id = nid()
    aggregator_id = nid()
    publisher_id = nid()
    das_port = das_opcua_port(line_id)
    flow: list[dict] = []

    # tab
    flow.append({
        "id": tab_id, "type": "tab",
        "label": f"{line_id} DAS",
        "disabled": False, "info": "",
    })

    # 상단 주석
    flow.append(comment_node(
        tab_id, f"{line_id} 라인 DAS",
        info=(
            f"라인 {line_id} 9개 설비 폴링 -> 1초마다 통합 페이로드 발행\n"
            f"OPC UA Server :{das_port} 로 노출 "
            f"(endpoint=line-das/{line_id})\n\n"
            f"payload schema: docs/integration_spec.md §5\n"
            f"host_mode: {host_mode}\n\n"
            f"reader -> [AGGREGATE TAGS] -> flow context\n"
            f"poll 1s -> [EMIT PAYLOAD] -> [DEBUG] + [PUBLISH OPC UA]\n"
        ),
        x=180, y=30,
    ))

    # --- aggregator function 노드 (모든 reader 가 여기로 보냄) ---
    agg_node = {
        "id": aggregator_id, "type": "function", "z": tab_id,
        "name": "AGGREGATE TAGS",
        "func": AGGREGATE_TAGS_CODE,
        "outputs": 0,
        "noerr": 0, "initialize": "", "finalize": "", "libs": [],
        "x": 1000, "y": 600, "wires": [],
    }
    flow.append(agg_node)

    # --- emit payload (1초 trigger) ---
    equipments_json = json.dumps([spec[0] for spec in gen.EQUIPMENT_SPECS])
    # 설비별 기대 tag 목록 (sensor + setpoint + power 등) — 아직 읽힌 적 없는
    # tag 도 EMIT 시점에 null 로 보내서 통합 DAS 가 변수를 찾을 수 있게 함.
    expected_tags = {}
    for sp in gen.EQUIPMENT_SPECS:
        eq, proto, _bp, fn, csec, _ss = sp
        tag_list = gen.build_tags(fn(), proto, csec, eq)
        expected_tags[eq] = {t["name"]: t["data_type"] for t in tag_list}
    expected_tags_json = json.dumps(expected_tags)
    nominal_cycle = {sp[0]: sp[4] for sp in gen.EQUIPMENT_SPECS}
    emit_code = EMIT_PAYLOAD_CODE_TMPL % {
        "line_id": line_id,
        "equipments_json": equipments_json,
        "expected_tags_json": expected_tags_json,
        "nominal_cycle_json": json.dumps(nominal_cycle),
    }
    dbg_payload = debug_node(tab_id, "payload", 1400, 100, complete="payload")
    flow.append(dbg_payload)

    # publish opcua server 노드 (라인별 포트)
    opcua_server = make_opcua_server_node(
        tab_id, line_id, das_port, x=1700, y=200,
    )
    flow.append(opcua_server)

    publish_code = PUBLISH_PAYLOAD_CODE_TMPL % {
        "expected_tags_json": expected_tags_json,
    }
    publish_node = function_node(
        tab_id, "PUBLISH -> OPC UA Server",
        publish_code,
        x=1400, y=200,
        outputs=1, wires=[[opcua_server["id"]]],
    )
    publish_node["id"] = publisher_id
    flow.append(publish_node)

    emit_node = function_node(
        tab_id, "EMIT PAYLOAD",
        emit_code, x=1100, y=160,
        outputs=1, wires=[[dbg_payload["id"], publisher_id]],
    )
    flow.append(emit_node)

    flow.append(inject_node(
        tab_id, "tick 1s", "", "date",
        x=900, y=160, repeat="1", once=True, once_delay="3",
        wires=[emit_node["id"]],
    ))

    # ====================================================================
    # 각 설비별 reader 컬럼 구성
    # ====================================================================

    for col_idx, spec in enumerate(gen.EQUIPMENT_SPECS):
        eq_id, proto, base_port, fn, cycle_sec, serial_slot = spec
        equipment_name = f"{line_id}_{eq_id}"
        tags = gen.build_tags(fn(), proto, cycle_sec, eq_id)

        col_y_offset = 100 + col_idx * 600  # 설비별로 세로 분리

        # 설비별 디버그 노드 — raw read 결과(parser 출력) 를 사이드바에서 확인·결과 눔기 의도
        eq_debug = debug_node(
            tab_id, f"{eq_id} debug",
            x=900, y=col_y_offset + 40,
            complete="payload", to_console=False,
        )
        flow.append(eq_debug)
        eq_debug_id = eq_debug["id"]

        if proto in ("modbus-rtu", "modbus-rtu-tcp"):
            # CNC — 시뮬이 ModbusRtuFramer + TCP listen 으로 직접 RTU 프레임을 서브 (Moxa NPort 스타일).
            # Node-RED 는 해당 CNC 컨테이너명을 host 로 쓰고
            # clienttype='tcp' + tcpType='TCP-RTU-BUFFERED' 로 접속.
            rtu_tcp_port = 5100 + serial_slot  # 5101/5102/5103
            rtu_host = f"cnc-{serial_slot:02d}"  # docker-compose hostname
            flow.append(comment_node(
                tab_id, f"{eq_id} (RTU-over-TCP) {rtu_host}:{rtu_tcp_port}",
                info=(f"{equipment_name} — RTU framing on TCP (Moxa NPort 스타일)\n"
                      f"host = {rtu_host}:{rtu_tcp_port}\n"
                      f"slave_id=1\n"
                      f"Node-RED clienttype=tcp / tcpType=TELNET (사용자 실측 OK)"),
                x=300, y=col_y_offset,
            ))
            _build_modbus_column(
                flow, tab_id, eq_id, equipment_name,
                host=rtu_host, port=rtu_tcp_port, tags=tags,
                aggregator_id=aggregator_id,
                y_base=col_y_offset + 40,
                transport="tcp", tcp_type="TELNET", serial_port="",
                equipment_debug_id=eq_debug_id,
            )
            continue

        port = line_port(base_port, line_id)
        host = host_for(eq_id, host_mode)
        flow.append(comment_node(
            tab_id, f"{eq_id} ({proto}) :{port}",
            info=f"{equipment_name} @ {host}:{port}",
            x=300, y=col_y_offset,
        ))

        if proto == "modbus":
            _build_modbus_column(
                flow, tab_id, eq_id, equipment_name,
                host, port, tags, aggregator_id,
                y_base=col_y_offset + 40,
                transport="tcp", serial_port="",
                equipment_debug_id=eq_debug_id,
            )
        elif proto == "mcprotocol":
            _build_mc_column(
                flow, tab_id, eq_id, equipment_name,
                host, port, tags, aggregator_id,
                y_base=col_y_offset + 40,
                equipment_debug_id=eq_debug_id,
            )
        elif proto == "opcua":
            _build_opcua_column(
                flow, tab_id, eq_id, equipment_name,
                host, port, tags, aggregator_id,
                y_base=col_y_offset + 40,
                equipment_debug_id=eq_debug_id,
            )
        else:
            raise ValueError(f"unknown protocol: {proto}")

    # ====================================================================
    # 자체 OPC UA Server 검증용 self-check 탭
    # ====================================================================
    flow.extend(build_selfcheck_tab(line_id, das_port))

    # ====================================================================
    # SP Control 탭 (각 설비 setpoint inject)
    # ====================================================================
    flow.extend(build_sp_control_tab(line_id, host_mode))

    return flow


# ---------------------------------------------------------------------------
# Self-Check 탭
# ---------------------------------------------------------------------------
# 라인 DAS 는 자기 자신의 OpcUa-Server (같은 Node-RED 프로세스 내) 에 publish 하는데,
# 그 값이 실제로 주소공간에 등록되어 client 에게 읽혀지는지 확인하는 곳.
# 여기서 값이 잘 읽히면 서버 측은 OK → 통합 DAS 에서 못 읽으면 docker/network 문제.
# 여기서도 못 읽으면 publish 코드 또는 OpcUa-Server 등록 문제.
def build_selfcheck_tab(line_id: str, das_port: int) -> list[dict]:
    tab_id = nid()
    endpoint_id = nid()
    nodes: list[dict] = []
    endpoint_host = f"nodered-{line_id.lower().replace('-', '')}"

    nodes.append({
        "id": tab_id, "type": "tab",
        "label": f"{line_id} Self-Check",
        "disabled": False, "info": "",
    })

    nodes.append(comment_node(
        tab_id, f"{line_id} self-check",
        info=(
            f"  같은 Docker 네트워크의 OpcUa-Server ({endpoint_host}:{das_port}) 에\n"
            f"client 로 접속해서 publish 된 변수를 읽어보는 검증 탭.\n\n"
            f"• 여기서 값이 읽히면 → 서버 측 OK\n"
            f"• 여기서도 안 읽히면 → publish/addVariable 문제\n"
            f"• 여기는 OK 인데 통합 DAS 가 안 읽히면 → docker/network 문제\n"
        ),
        x=180, y=30,
    ))

    # endpoint config: use the Docker service hostname so endpointUri matches
    # what node-opcua advertises. 127.0.0.1 can fail endpoint selection here.
    ep_cfg = make_opcua_endpoint_config(
        endpoint_id, endpoint_host, das_port,
    )
    ep_cfg["endpoint"] = f"opc.tcp://{endpoint_host}:{das_port}/line-das/{line_id}"
    ep_cfg["name"] = f"self-check {line_id}"
    nodes.append(ep_cfg)

    # 샘플 변수 목록 (라인당 대표 몇 개만)
    sample_tags = [
        (f"{line_id}.payload",                  "String"),
        (f"{line_id}.line_ts",                  "String"),
        (f"{line_id}.CAST-01.status",           "String"),
        (f"{line_id}.CAST-01.data.power",       "Float"),
        (f"{line_id}.CNC-01.status",            "String"),
        (f"{line_id}.CNC-01.data.power",        "Float"),
        (f"{line_id}.TEST-01.status",           "String"),
        (f"{line_id}.TEST-01.data.hole_dimension", "Float"),
    ]

    # 1초 주기 inject (이 탭 전용, 라인 DAS 의 tick 과 독립)
    tick_id = nid()
    item_inputs: list[str] = []

    for i, (tag, dtype) in enumerate(sample_tags):
        y = 100 + i * 60
        # debug node
        dbg = debug_node(
            tab_id, f"SELF-CHECK {tag}",
            x=900, y=y,
            complete="payload", to_console=True,
        )
        nodes.append(dbg)
        # client (READ)
        client = opcua_client(
            tab_id, endpoint_id, action="read", name="read",
            x=700, y=y, wires=[dbg["id"]],
        )
        nodes.append(client)
        # item
        item = opcua_item(
            tab_id, tag, dtype,
            x=480, y=y, wires=[client["id"]],
        )
        nodes.append(item)
        item_inputs.append(item["id"])

    # once_delay 를 충분히 (15s) 둔다 — OpcUa-Server 시작 +
    # 라인 DAS 가 첫 tick addVariable, 2번째 tick Variable update 구조라
    # 최소 2–3초간 publish 이후에 self-check 가 read 돌아가야 함.
    nodes.append(inject_node(
        tab_id, "self-check tick 1s", "", "date",
        x=250, y=100, repeat="1", once=True, once_delay="15",
        wires=item_inputs,
    ))
    _ = tick_id  # 예약 변수 (helper 가 자체 nid 생성)

    return nodes


# ---------------------------------------------------------------------------
# SP Control 탭
# ---------------------------------------------------------------------------
# 각 설비의 setpoint tag 마다 inject 노드를 두고,
# 눌러면 해당 프로토콜(modbus / mc / opcua) 로 value 를 설비 시뮬에 write 한다.
# 시뮬 측은 이미 external write 를 감지해 setpoint 를 갱신하고,
# 갱신된 setpoint 이 센서 노이즈의 중심값이 되므로
# DAS 탭 의 값이 실제로 따라 움직이는 것을 확인 가능.
#
# UI 구조 (한 설비 = 가로 행):
#   [inject low]  ----\
#   [inject mid]  ----- [(prep ->) write 노드]
#   [inject high] ----/
def build_sp_control_tab(line_id: str, host_mode: str) -> list[dict]:
    tab_id = nid()
    nodes: list[dict] = []

    nodes.append({
        "id": tab_id, "type": "tab",
        "label": f"{line_id} SP Control",
        "disabled": False, "info": "",
    })

    nodes.append(comment_node(
        tab_id, f"{line_id} setpoint write",
        info=(
            f"· 설비 setpoint 를 넎트워크 너머 변경하는 탭.\n"
            f"· 프로토콜별 write 노드를 사용, 시뮬에 값을 기록.\n"
            f"· inject 세 개 (low / mid / high) 이 기본 제공됨.\n"
            f"· write 후 1~2초 내 DAS 탭 의 센서 값들이 새 중심값으로 이동.\n"
        ),
        x=180, y=30,
    ))

    # 설비별로 세로 본들을 나눠서 배치
    col_y = 80

    for spec in gen.EQUIPMENT_SPECS:
        eq_id, proto, base_port, fn, cycle_sec, serial_slot = spec
        equipment_name = f"{line_id}_{eq_id}"
        tags = gen.build_tags(fn(), proto, cycle_sec, eq_id)
        sp_tags = [t for t in tags if t.get("role") == "setpoint"]
        if not sp_tags:
            continue

        # 프로토콜별 host/port 결정
        if proto in ("modbus-rtu", "modbus-rtu-tcp"):
            host = f"cnc-{serial_slot:02d}"
            port = 5100 + serial_slot
            transport, tcp_type = "tcp", "TELNET"
        else:
            host = host_for(eq_id, host_mode)
            port = line_port(base_port, line_id)
            transport, tcp_type = "tcp", "DEFAULT"

        # 설비 섹션 레이블
        nodes.append(comment_node(
            tab_id, f"{eq_id} ({proto}) {host}:{port}",
            info=f"{equipment_name} setpoint write 세그먼트",
            x=200, y=col_y,
        ))
        col_y += 30

        # 프로토콜별 config (한 설비당 1개)
        if proto in ("modbus", "modbus-rtu", "modbus-rtu-tcp"):
            mapping = build_modbus_mapping(tags)
            server_id = nid()
            nodes.append(make_modbus_client_config(
                server_id, host, port, f"{equipment_name} SP",
                transport=transport, tcp_type=tcp_type, serial_port="",
            ))
        elif proto == "mcprotocol":
            conn_id = nid()
            nodes.append(make_mc_connection_config(
                conn_id, host, port, f"{equipment_name} SP",
            ))
        elif proto == "opcua":
            endpoint_id = nid()
            nodes.append(make_opcua_endpoint_config(endpoint_id, host, port))
        else:
            continue

        # 각 setpoint tag 마다 가로 row
        for sp in sp_tags:
            name = sp["name"]
            dt = sp["data_type"]
            base = sp.get("base_value", 0)

            # low / mid / high 값 흐트
            if dt == "int":
                lo = int(round(base * 0.9))
                mid = int(round(base))
                hi = int(round(base * 1.1))
                p_type = "num"
            elif dt == "float":
                lo = round(base * 0.9, 3)
                mid = round(base, 3)
                hi = round(base * 1.1, 3)
                p_type = "num"
            else:
                # bool setpoint 는 현재 모델에 없음
                lo, mid, hi = 0, 0, 1
                p_type = "bool"

            # write 노드(행 우측) 먼저 생성 -> inject 가 이걸 가리키게
            write_node_id: str
            row_y = col_y

            if proto in ("modbus", "modbus-rtu", "modbus-rtu-tcp"):
                kind, addr = mapping[name]
                if kind == "coil":
                    w = mb_write(tab_id, server_id,
                                 f"{eq_id} write {name}",
                                 "Coil", str(addr), "1",
                                 x=850, y=row_y)
                elif kind == "hr_int":
                    w = mb_write(tab_id, server_id,
                                 f"{eq_id} write {name}",
                                 "HoldingRegister", str(addr), "1",
                                 x=850, y=row_y)
                elif kind == "hr_float":
                    # float -> 2 word BE.
                    # ⚠️ 중요: 2 register 동시 쓰기는 FC=16 (Write Multiple Registers).
                    #    contrib-modbus 의 dataType 을 "MHoldingRegisters" 로 둡다.
                    #    "HoldingRegister" 는 FC=6 (single) 이라서 quantity=2 여도 첫 register 만 쓰고
                    #    float write 가 실패한다.
                    w = mb_write(tab_id, server_id,
                                 f"{eq_id} write {name}",
                                 "MHoldingRegisters", str(addr), "2",
                                 x=900, y=row_y)
                else:
                    continue
                nodes.append(w)
                # float 이면 prep function 추가
                if kind == "hr_float":
                    prep_code = (
                        "// float -> uint16[2] BE 변환\n"
                        "// 시뮬 modbus_server 가 readFloatBE(reg0_hi << 16 | reg1_lo) 로 해석하므로\n"
                        "// register 순서는 [hi_word, lo_word] (big-endian word order)\n"
                        "// modbus-write 노드가 dataType=MHoldingRegisters 면 FC=16, payload 가 array 면\n"
                        "// 그대로 values 로 쓴다 (contrib-modbus modbus-write.js#105).\n"
                        "const v = Number(msg.payload);\n"
                        "if (!Number.isFinite(v)) {\n"
                        "  node.warn('invalid payload for float write: ' + msg.payload);\n"
                        "  return null;\n"
                        "}\n"
                        "const buf = Buffer.alloc(4);\n"
                        "buf.writeFloatBE(v, 0);\n"
                        "const hi = buf.readUInt16BE(0);\n"
                        "const lo = buf.readUInt16BE(2);\n"
                        "msg.payload = [hi, lo];\n"
                        "return msg;\n"
                    )
                    prep = function_node(tab_id, f"f32->u16x2 {name}",
                                         prep_code, x=700, y=row_y,
                                         outputs=1, wires=[[w["id"]]])
                    nodes.append(prep)
                    write_input_id = prep["id"]
                else:
                    write_input_id = w["id"]

            elif proto == "mcprotocol":
                # MC address 결정: float는 DREAL<addr>, int 는 D<addr>, bool 은 M<addr>
                # ⚠️ 중요: contrib-mcprotocol 의 mcprotocol.js 의
                #   prepareWritePacket 은 case 'REAL' 만 있고 'FLOAT' 는 없어서
                #   DFLOAT 로 쓰면 'Unknown data type FLOAT' 에러 발생.
                #   read 쪽은 case 'FLOAT' 와 case 'REAL' 둘 다 지원하므로
                #   DREAL 로 통일하면 read/write 모두 동작.
                mc = sp.get("mc", {})
                device = mc.get("device", "D")
                addr = mc.get("address", 0)
                if device == "M":
                    address_str = f"M{addr}"
                elif device == "D" and dt == "int":
                    address_str = f"D{addr}"
                elif device == "D" and dt == "float":
                    address_str = f"DREAL{addr}"
                else:
                    continue
                w = mc_write(tab_id, conn_id, f"{eq_id} write {name}",
                             address_str, x=850, y=row_y)
                nodes.append(w)
                write_input_id = w["id"]

            elif proto == "opcua":
                opc_dt = opcua_dtype(dt)
                client = opcua_client(tab_id, endpoint_id, "write",
                                      f"{eq_id} write {name}",
                                      x=900, y=row_y, wires=[])
                nodes.append(client)
                prep = opcua_write_prep(tab_id, name, opc_dt,
                                        x=700, y=row_y,
                                        wires=[client["id"]])
                nodes.append(prep)
                write_input_id = prep["id"]
            else:
                continue

            # inject 세 개 (low / mid / high) — 각각 50px 간격으로 상하로 배치
            for i, (label, val) in enumerate([
                ("low", lo), ("mid", mid), ("high", hi),
            ]):
                inj = inject_node(
                    tab_id, f"{name} = {val} ({label})",
                    str(val), p_type,
                    x=420, y=row_y + i * 25,
                    repeat="", once=False, once_delay="",
                    wires=[write_input_id],
                )
                nodes.append(inj)

            col_y = row_y + 90  # 다음 row

        col_y += 30  # 설비간 여백

    return nodes


# ---------------------------------------------------------------------------
# Modbus 컬럼
# ---------------------------------------------------------------------------

def _build_modbus_column(flow: list[dict], z: str, eq_id: str,
                         equipment_name: str, host: str, port: int,
                         tags: list[dict], aggregator_id: str,
                         y_base: int,
                         transport: str = "tcp",
                         tcp_type: str = "DEFAULT",
                         serial_port: str = "",
                         equipment_debug_id: str = "") -> None:
    # parser 출력 wires 에 설비별 디버그 ID 동보
    parser_wires = [aggregator_id]
    if equipment_debug_id:
        parser_wires.append(equipment_debug_id)
    mapping = build_modbus_mapping(tags)

    server_id = nid()
    flow.append(make_modbus_client_config(
        server_id, host, port, equipment_name,
        transport=transport, tcp_type=tcp_type, serial_port=serial_port,
    ))

    # bool 들 (coils)
    bool_tags = sorted(
        [t for t in tags if t["data_type"] == "bool"],
        key=lambda t: mapping[t["name"]][1],
    )
    if bool_tags:
        names = [t["name"] for t in bool_tags]
        addrs = [mapping[n][1] for n in names]
        adr_base = min(addrs)
        qty = max(addrs) - adr_base + 1
        parser = function_node(
            z, f"{eq_id} parse coils",
            MODBUS_PARSE_TMPL % {
                "kind": "coil",
                "names_json": json.dumps(names),
                "adr_base": adr_base,
                "equipment_id": eq_id,
            },
            x=600, y=y_base, wires=[list(parser_wires)],
        )
        flow.append(parser)
        flow.append(mb_read(
            z, server_id, f"{eq_id} read coils[{adr_base}..{adr_base + qty - 1}]",
            "Coil", str(adr_base), str(qty),
            x=300, y=y_base, out_wires=[parser["id"]],
        ))
        y_base += 50

    # int (HR 0..)
    int_tags = sorted(
        [t for t in tags if t["data_type"] == "int"],
        key=lambda t: mapping[t["name"]][1],
    )
    if int_tags:
        names = [t["name"] for t in int_tags]
        addrs = [mapping[n][1] for n in names]
        adr_base = min(addrs)
        qty = max(addrs) - adr_base + 1
        parser = function_node(
            z, f"{eq_id} parse int",
            MODBUS_PARSE_TMPL % {
                "kind": "hr_int",
                "names_json": json.dumps(names),
                "adr_base": adr_base,
                "equipment_id": eq_id,
            },
            x=600, y=y_base, wires=[list(parser_wires)],
        )
        flow.append(parser)
        flow.append(mb_read(
            z, server_id, f"{eq_id} HR[{adr_base}..{adr_base + qty - 1}] int",
            "HoldingRegister", str(adr_base), str(qty),
            x=300, y=y_base, out_wires=[parser["id"]],
        ))
        y_base += 50

    # float (HR 1000..)
    float_tags = sorted(
        [t for t in tags if t["data_type"] == "float"],
        key=lambda t: mapping[t["name"]][1],
    )
    if float_tags:
        names = [t["name"] for t in float_tags]
        addrs = [mapping[n][1] for n in names]
        adr_base = min(addrs)
        # 2 word/float, build_mapping 이 FLOAT_BASE+i*2 로 부여하므로 연속
        qty = len(names) * 2
        parser = function_node(
            z, f"{eq_id} parse float (BE)",
            MODBUS_PARSE_TMPL % {
                "kind": "hr_float",
                "names_json": json.dumps(names),
                "adr_base": adr_base,
                "equipment_id": eq_id,
            },
            x=600, y=y_base, wires=[list(parser_wires)],
        )
        flow.append(parser)
        flow.append(mb_read(
            z, server_id, f"{eq_id} HR[{adr_base}..{adr_base + qty - 1}] float",
            "HoldingRegister", str(adr_base), str(qty),
            x=300, y=y_base, out_wires=[parser["id"]],
        ))


# ---------------------------------------------------------------------------
# MC Protocol 컬럼
# ---------------------------------------------------------------------------

def _build_mc_column(flow: list[dict], z: str, eq_id: str,
                     equipment_name: str, host: str, port: int,
                     tags: list[dict], aggregator_id: str,
                     y_base: int,
                     equipment_debug_id: str = "") -> None:
    parser_wires = [aggregator_id]
    if equipment_debug_id:
        parser_wires.append(equipment_debug_id)
    conn_id = nid()
    flow.append(make_mc_connection_config(conn_id, host, port, equipment_name))

    # tag 별로 mc_read 노드 1개씩, 폴링 inject 1개가 fan-out
    read_node_ids: list[str] = []
    y = y_base
    for t in tags:
        mc = t["mc"]
        device = mc["device"]
        addr = mc["address"]
        dt = t["data_type"]

        if device == "M":
            address_str = f"M{addr}"
            mc_type = "bit"
        elif device == "D" and dt == "int":
            address_str = f"D{addr}"
            mc_type = "int"
        elif device == "D" and dt == "float":
            # DREAL (READ→case REAL, WRITE→case REAL 둘 다 OK)
            # DFLOAT 는 write 에서 'Unknown data type FLOAT' 발생
            address_str = f"DREAL{addr}"
            mc_type = "float"
        else:
            raise ValueError(f"unsupported MC mapping: {t['name']} {device}{addr} {dt}")

        parser = function_node(
            z, f"{eq_id} {t['name']}",
            MC_PARSE_TMPL % {
                "names_json": json.dumps([t["name"]]),
                "type": mc_type,
                "equipment_id": eq_id,
            },
            x=700, y=y, wires=[list(parser_wires)],
        )
        flow.append(parser)
        read = mc_read(z, conn_id, f"{eq_id} read {address_str}",
                       address_str, x=400, y=y, wires=[parser["id"]],
                       output_format=1)  # array format -> 일관성
        flow.append(read)
        read_node_ids.append(read["id"])
        y += 28

    # 폴링 inject: 1초마다 모든 mc_read 트리거
    flow.append(inject_node(
        z, f"{eq_id} poll 1s", "", "date",
        x=180, y=y_base, repeat="1", once=True, once_delay="3",
        wires=read_node_ids,
    ))


# ---------------------------------------------------------------------------
# OPC UA 컬럼
# ---------------------------------------------------------------------------

def _build_opcua_column(flow: list[dict], z: str, eq_id: str,
                        equipment_name: str, host: str, port: int,
                        tags: list[dict], aggregator_id: str,
                        y_base: int,
                        equipment_debug_id: str = "") -> None:
    parser_wires = [aggregator_id]
    if equipment_debug_id:
        parser_wires.append(equipment_debug_id)
    endpoint_id = nid()
    flow.append(make_opcua_endpoint_config(endpoint_id, host, port))

    # 각 tag -> prep function(msg.topic/datatype 세팅) -> read client -> COLLECT 누적
    # OpcUa-Item 노드가 일부 버전에서 msg.messageType/variableName 을 채워주지 않아
    # "messageType, namespace, variableName or VariableValue is missing" 경고가 뜨므로
    # 여기서는 직접 function 노드에서 msg 를 구성해 client 에 넘긴다.
    collect_id = nid()
    collect_code = OPCUA_COLLECT_TMPL % {"equipment_id": eq_id}
    flow.append({
        "id": collect_id, "type": "function", "z": z,
        "name": f"{eq_id} collect",
        "func": collect_code,
        "outputs": 0,
        "noerr": 0, "initialize": "", "finalize": "", "libs": [],
        "x": 800, "y": y_base, "wires": [],
    })

    prep_ids: list[str] = []
    y = y_base
    names = [t["name"] for t in tags]
    for t in tags:
        dt = opcua_dtype(t["data_type"])
        node_id = f"ns=2;s={t['name']}"
        prep_code = (
            "// OpcUa-Client read 용 msg 구성 (Item 노드 제거)\n"
            f"msg.topic = {node_id!r};\n"
            f"msg.datatype = {dt!r};\n"
            f"msg.opcuaItemName = {t['name']!r};\n"
            "msg.payload = '';\n"
            "return msg;\n"
        )
        client = opcua_client(z, endpoint_id, "read", f"{eq_id} read {t['name']}",
                              x=580, y=y, wires=[collect_id])
        prep = function_node(
            z, f"{eq_id} prep {t['name']}",
            prep_code,
            x=380, y=y, wires=[[client["id"]]],
        )
        flow.append(prep)
        flow.append(client)
        prep_ids.append(prep["id"])
        y += 28

    # 폴링 inject: 1초마다 모든 prep function 트리거
    flow.append(inject_node(
        z, f"{eq_id} poll 1s", "", "date",
        x=200, y=y_base, repeat="1", once=True, once_delay="3",
        wires=prep_ids,
    ))

    # flush: 폴링 후 약간 늦게 누적된 값을 aggregator 로 일괄 전송
    flush_code = OPCUA_FLUSH_TMPL % {
        "equipment_id": eq_id,
        "names_json": json.dumps(names),
    }
    flush_node = function_node(
        z, f"{eq_id} flush",
        flush_code,
        x=1000, y=y_base, wires=[list(parser_wires)],
    )
    flow.append(flush_node)

    # 별도 inject (1초, 약간 늦게 발사)
    flow.append(inject_node(
        z, f"{eq_id} flush tick", "", "date",
        x=820, y=y_base + 30, repeat="1", once=True, once_delay="3.5",
        wires=[flush_node["id"]],
    ))


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="라인 1개 DAS Node-RED flow 빌더 (docker 1라인 모델)",
    )
    parser.add_argument(
        "--line-id", default=os.environ.get("LINE_ID", "LINE-01"),
        help="라인 ID (기본: env LINE_ID 또는 'LINE-01')",
    )
    parser.add_argument(
        "--host-mode", choices=("docker", "localhost"), default="docker",
        help="docker: 컨테이너명(eq_id 소문자) / localhost: 127.0.0.1",
    )
    parser.add_argument(
        "--out", default=None,
        help="출력 파일 (기본: nodered/flows_das.json)",
    )
    args = parser.parse_args()

    flow = build_line_flow(args.line_id, args.host_mode)
    out_path = Path(args.out) if args.out else \
        Path(__file__).parent / "flows_das.json"
    out_path.write_text(json.dumps(flow, indent=2, ensure_ascii=False),
                        encoding="utf-8")
    print(f"wrote {out_path} ({len(flow)} nodes) line_id={args.line_id} "
          f"host_mode={args.host_mode}")


if __name__ == "__main__":
    main()
