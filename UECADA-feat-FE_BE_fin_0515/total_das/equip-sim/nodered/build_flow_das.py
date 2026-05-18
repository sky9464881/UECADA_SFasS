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

# Node-RED 라인 DAS OPC UA Server 포트 (LINE-01: 4870, LINE-02: 4970, LINE-03: 5070)
DAS_OPCUA_BASE = 4870
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

FLOAT_BASE = 1000


def build_modbus_mapping(tags: list[dict]) -> dict[str, tuple[str, int]]:
    mapping: dict[str, tuple[str, int]] = {}
    coil_i = int_i = float_i = 0
    for t in tags:
        dt = t["data_type"]
        if dt == "bool":
            mapping[t["name"]] = ("coil", coil_i)
            coil_i += 1
        elif dt == "int":
            mapping[t["name"]] = ("hr_int", int_i)
            int_i += 1
        elif dt == "float":
            mapping[t["name"]] = ("hr_float", FLOAT_BASE + float_i * 2)
            float_i += 1
        else:
            raise ValueError(f"unknown data_type: {dt}")
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
                              equipment_name: str) -> dict:
    return {
        "id": server_id, "type": "modbus-client",
        "name": f"{equipment_name} @ {host}:{port}",
        "clienttype": "tcp",
        "bufferCommands": True,
        "stateLogEnabled": False, "queueLogEnabled": False, "failureLogEnabled": True,
        "tcpHost": host, "tcpPort": str(port), "tcpType": "DEFAULT",
        "serialPort": "", "serialType": "RTU-BUFFERD",
        "serialBaudrate": "9600", "serialDatabits": "8",
        "serialStopbits": "1", "serialParity": "none",
        "serialConnectionDelay": "100", "serialAsciiResponseStartDelimiter": "",
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

def make_opcua_endpoint_config(endpoint_id: str, host: str, port: int,
                               equipment_name: str) -> dict:
    return {
        "id": endpoint_id, "type": "OpcUa-Endpoint",
        "endpoint": f"opc.tcp://{host}:{port}/{equipment_name}/",
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
# 페이로드 aggregate / emit / publish 코드
# ---------------------------------------------------------------------------

AGGREGATE_TAGS_CODE = """\
// 각 설비 reader 가 보낸 부분 dict 를 flow context 에 누적
const eq = msg.equipment_id;
if (!eq) return null;
const buf = flow.get('equipments') || {};
const prev = buf[eq] || { data: {} };
prev.data = Object.assign(prev.data || {}, msg.tags || {});
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
  const { status, quality } = classify(data, slot.last_update_ms);

  // ----- cycle_time 산출 -----
  // progress 를 더해 1.0 이상이 되면 그 순간의 경과시간을 cycle_time 으로 저장.
  // 간단한 구현: tick 당 1초 증가로 가정, accumulator 가 1.0 되면 reset.
  let st = cyc[eq_id] || { acc: 0.0, last_cycle_time: null, start_ms: now };
  if (status === 'RUN' && typeof data.progress === 'number') {
    st.acc += data.progress;
    if (st.acc >= 1.0) {
      const dt = (now - (st.start_ms || now)) / 1000.0;
      st.last_cycle_time = +dt.toFixed(2);
      st.acc = 0.0;
      st.start_ms = now;
    }
  } else if (status === 'OFF') {
    // OFF 일 때 눌적치 유지시켜도 OK. 다음 RUN 에서 이어서 계산.
  }
  cyc[eq_id] = st;
  if (st.last_cycle_time !== null) data.cycle_time = st.last_cycle_time;

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
return { equipment_id: "%(equipment_id)s", tags: tags };
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
return { equipment_id: "%(equipment_id)s", tags: tags };
"""


OPCUA_COLLECT_TMPL = """\
// OPC UA Item read 결과를 누적 후 aggregator 로 일괄 전송
// 각 read 결과는 msg.payload 에 단일값, msg.topic 에 NodeId.
const EQ = "%(equipment_id)s";
const TAG = msg.opcuaItemName || msg.topic || "";
// item name 은 NodeId 의 s=... 부분
let name = TAG;
const m = String(TAG).match(/s=([^;]+)$/);
if (m) name = m[1];
const buf = flow.get('opcua_buf_%(equipment_id)s') || {};
buf[name] = msg.payload;
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
return { equipment_id: EQ, tags: tags };
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
        "usersFile": "",
        "nodesetDir": "",
        "autoAcceptUnknownCertificate": True,
        "maxNodesPerBrowse": 0, "maxNodesPerHistoryReadData": 0,
        "maxNodesPerHistoryReadEvents": 0, "maxNodesPerWrite": 0,
        "maxNodesPerMethodCall": 0, "maxNodesPerRegisterNodes": 0,
        "maxNodesPerNodeManagement": 0, "maxMonitoredItemsPerCall": 0,
        "maxNodesPerHistoryUpdateData": 0, "maxNodesPerHistoryUpdateEvents": 0,
        "maxNodesPerRead": 0, "maxNodesPerTranslateBrowsePathsToNodeIds": 0,
        "name": f"DAS Server {line_id} :{port}",
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


PUBLISH_PAYLOAD_CODE = """\
// payload 를 OPC UA Server 변수로 발행
//
// node-red-contrib-opcua 의 OpcUa-Server 는 2 단계 프로토콜을 쓴다:
//
//  1) 최초 1회: addVariable 명령으로 주소 공간에 새 노드 등록
//       msg.topic   = 'ns=2;s=<name>;datatype=<DT>'
//       msg.payload = { opcuaCommand: 'addVariable' }
//
//  2) 이후 매 tick: Variable 명령으로 값 갱신
//       msg.payload = {
//         messageType: 'Variable', namespace: 2,
//         variableName: '<name>', variableValue: <val>, datatype: '<DT>'
//       }
//
// 필드가 하나라도 빠지면 OpcUa-Server 가
// "warning: properties like messageType, namespace, variableName or
//  VariableValue is missing." 경고를 한 메세지당 뜨운다.
const p = msg.payload;
if (!p) return null;
const lineId = p.line_id;
const registered = flow.get('opcua_registered') || {};
const outs = [];
const DATA_TYPES = {
  power: 'Boolean',
  progress: 'Double',
  cycle_time: 'Double',
  injection_pressure_sp: 'Double',
  mold_temperature_sp: 'Double',
  injection_pressure: 'Double',
  mold_temperature: 'Double',
  cooling_flow: 'Double',
  spindle_speed_sp: 'Int32',
  spindle_speed: 'Int32',
  tool_usage: 'Double',
  coolant_flow: 'Double',
  cleaning_temperature_sp: 'Double',
  cleaning_concentration: 'Double',
  cleaning_temperature: 'Double',
  cleaning_pressure: 'Double',
  cleaning_flow: 'Double',
  tightening_torque_sp: 'Double',
  tightening_torque: 'Double',
  tightening_angle: 'Double',
  press_force: 'Double',
  part_detected: 'Boolean',
  bore_dimension: 'Double',
  hole_dimension: 'Double',
  leak_rate: 'Double',
  flow_value: 'Double',
  result_ok: 'Boolean',
};

function datatypeFor(tag, value) {
  if (DATA_TYPES[tag]) return DATA_TYPES[tag];
  if (typeof value === 'boolean') return 'Boolean';
  if (typeof value === 'number') return 'Double';
  return 'String';
}

function send(name, value, dtype) {
  // 최초 1회만 addVariable, 그 다음부터는 Variable
  if (!registered[name]) {
    outs.push({
      topic: `ns=2;s=${name};datatype=${dtype}`,
      payload: { opcuaCommand: 'addVariable' },
    });
    registered[name] = true;
  }
  outs.push({
    payload: {
      messageType: 'Variable',
      namespace: 2,
      variableName: name,
      variableValue: value,
      datatype: dtype,
    },
  });
}

send(`${lineId}.payload`,         JSON.stringify(p),     'String');
send(`${lineId}.line_ts`,         p.ts,                   'String');
send(`${lineId}.schema_version`,  p.schema_version,       'String');
for (const [eq, slot] of Object.entries(p.equipments)) {
  send(`${lineId}.${eq}.status`,  slot.status,            'String');
  send(`${lineId}.${eq}.ts`,      slot.ts,                'String');
  send(`${lineId}.${eq}.quality`, slot.quality,           'String');
  for (const [tag, val] of Object.entries(slot.data || {})) {
    send(`${lineId}.${eq}.data.${tag}`, val, datatypeFor(tag, val));
  }
}
flow.set('opcua_registered', registered);
return [outs];
"""


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
    equipments_json = json.dumps([eq for eq, *_ in gen.EQUIPMENT_SPECS])
    emit_code = EMIT_PAYLOAD_CODE_TMPL % {
        "line_id": line_id,
        "equipments_json": equipments_json,
    }
    dbg_payload = debug_node(tab_id, "payload", 1400, 100, complete="payload")
    flow.append(dbg_payload)

    # publish opcua server 노드 (라인별 포트)
    opcua_server = make_opcua_server_node(
        tab_id, line_id, das_port, x=1700, y=200,
    )
    flow.append(opcua_server)

    publish_node = function_node(
        tab_id, "PUBLISH -> OPC UA Server",
        PUBLISH_PAYLOAD_CODE,
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

    for col_idx, (eq_id, proto, base_port, fn, cycle_sec) in enumerate(gen.EQUIPMENT_SPECS):
        port = line_port(base_port, line_id)
        host = host_for(eq_id, host_mode)
        equipment_name = f"{line_id}_{eq_id}"
        tags = gen.build_tags(fn(), proto, cycle_sec)

        col_y_offset = 100 + col_idx * 600  # 설비별로 세로 분리

        # 설비 주석
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
            )
        elif proto == "mcprotocol":
            _build_mc_column(
                flow, tab_id, eq_id, equipment_name,
                host, port, tags, aggregator_id,
                y_base=col_y_offset + 40,
            )
        elif proto == "opcua":
            _build_opcua_column(
                flow, tab_id, eq_id, equipment_name,
                host, port, tags, aggregator_id,
                y_base=col_y_offset + 40,
            )
        else:
            raise ValueError(f"unknown protocol: {proto}")

    return flow


# ---------------------------------------------------------------------------
# Modbus 컬럼
# ---------------------------------------------------------------------------

def _build_modbus_column(flow: list[dict], z: str, eq_id: str,
                         equipment_name: str, host: str, port: int,
                         tags: list[dict], aggregator_id: str,
                         y_base: int) -> None:
    mapping = build_modbus_mapping(tags)

    server_id = nid()
    flow.append(make_modbus_client_config(server_id, host, port, equipment_name))

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
            x=600, y=y_base, wires=[[aggregator_id]],
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
            x=600, y=y_base, wires=[[aggregator_id]],
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
            x=600, y=y_base, wires=[[aggregator_id]],
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
                     y_base: int) -> None:
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
            address_str = f"DFLOAT{addr}"
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
            x=700, y=y, wires=[[aggregator_id]],
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
                        y_base: int) -> None:
    endpoint_id = nid()
    flow.append(make_opcua_endpoint_config(endpoint_id, host, port, equipment_name))

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
        x=1000, y=y_base, wires=[[aggregator_id]],
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
