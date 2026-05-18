"""호스트 Node-RED (1883) 용 OPC UA 단순 조회 flow 빌더.

도커가 띄운 라인 DAS OPC UA Server 3개:
    opc.tcp://localhost:4870/line-das/LINE-01
    opc.tcp://localhost:4970/line-das/LINE-02
    opc.tcp://localhost:5070/line-das/LINE-03

를 호스트 Node-RED 에서 subscribe 해 debug 사이드바에서 조회한다.
용도: 단순 조회 (DAS 가 잘 노출되는지 확인 + 페이로드 모양 확인).

생성 결과 nodered/flows_host_viewer.json 을 호스트 Node-RED 에 import 하면 끝.

사용:
    python nodered/build_flow_host_viewer.py
    python nodered/build_flow_host_viewer.py --host 127.0.0.1 --lines LINE-01,LINE-02
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))


DAS_OPCUA_BASE = 4870
PORT_STRIDE = 100


def das_opcua_port(line_id: str) -> int:
    m = re.match(r"LINE-(\d+)$", line_id)
    if not m:
        raise ValueError(f"invalid LINE_ID: {line_id!r}")
    n = int(m.group(1))
    if n < 1:
        raise ValueError(f"line number must be >= 1, got {n}")
    return DAS_OPCUA_BASE + (n - 1) * PORT_STRIDE


def nid() -> str:
    return uuid.uuid4().hex[:16]


# ---------------------------------------------------------------------------
# function code: 구독할 NodeId 목록 만들기
# ---------------------------------------------------------------------------

SUBSCRIBE_TOPICS_CODE_TMPL = """\
// 단순 조회 flow — 라인 DAS payload 한 노드만 구독
// (전체 페이로드가 JSON 문자열로 들어오므로 이것 하나면 라인 전체 값 확인 가능)
//
// 개별 태그도 같이 보고 싶으면 아래 EXTRA_TOPICS 배열에 NodeId 를 추가하면 됨.
const LINE = "%(line_id)s";
const EXTRA_TOPICS = [
  // 예시 — 주석 풀어서 사용:
  // { name: `${LINE}.CAST-01.data.progress`,         datatype: 'Double'  },
  // { name: `${LINE}.CAST-01.status`,                datatype: 'String'  },
  // { name: `${LINE}.CNC-01.data.spindle_speed`,     datatype: 'Int32'   },
  // { name: `${LINE}.TEST-01.data.result_ok`,        datatype: 'Boolean' },
];

const out = [{ topic: `ns=2;s=${LINE}.payload;datatype=String` }];
for (const t of EXTRA_TOPICS) {
  out.push({ topic: `ns=2;s=${t.name};datatype=${t.datatype}` });
}
return [out];
"""


# inbound 메시지를 JSON 으로 파싱해 보기 좋게 정리
PARSE_PAYLOAD_CODE = """\
// 라인 DAS payload 노드면 JSON 문자열이므로 파싱해서 객체로 풀어준다
// (개별 태그 노드는 그대로 흘려보냄)
const topic = msg.topic || "";
if (typeof msg.payload === "string" && /\\.payload$/.test(topic)) {
  try {
    msg.payload = JSON.parse(msg.payload);
  } catch (e) {
    node.warn(`json parse fail for ${topic}: ${e.message}`);
  }
}
return msg;
"""


# ---------------------------------------------------------------------------
# 노드 헬퍼
# ---------------------------------------------------------------------------

def tab_node(label: str, info: str = "") -> dict:
    return {
        "id": nid(), "type": "tab",
        "label": label, "disabled": False, "info": info,
    }


def comment_node(z: str, name: str, info: str, x: int, y: int) -> dict:
    return {
        "id": nid(), "type": "comment", "z": z,
        "name": name, "info": info, "x": x, "y": y, "wires": [],
    }


def inject_node(z: str, name: str, x: int, y: int, wires: list,
                once_delay: str = "2") -> dict:
    return {
        "id": nid(), "type": "inject", "z": z,
        "name": name,
        "props": [{"p": "payload"}, {"p": "topic", "vt": "str"}],
        "repeat": "", "crontab": "",
        "once": True, "onceDelay": once_delay,
        "topic": "", "payload": "", "payloadType": "date",
        "x": x, "y": y, "wires": [wires],
    }


def function_node(z: str, name: str, func: str, x: int, y: int,
                  outputs: int, wires: list) -> dict:
    return {
        "id": nid(), "type": "function", "z": z,
        "name": name, "func": func,
        "outputs": outputs,
        "noerr": 0, "initialize": "", "finalize": "", "libs": [],
        "x": x, "y": y, "wires": wires,
    }


def split_node(z: str, x: int, y: int, wires: list) -> dict:
    return {
        "id": nid(), "type": "split", "z": z,
        "name": "split topics",
        "splt": "\\n", "spltType": "str",
        "arraySplt": 1, "arraySpltType": "len",
        "stream": False, "addname": "",
        "x": x, "y": y, "wires": [wires],
    }


def debug_node(z: str, name: str, x: int, y: int,
               complete: str = "payload") -> dict:
    return {
        "id": nid(), "type": "debug", "z": z,
        "name": name, "active": True,
        "tosidebar": True, "console": False, "tostatus": False,
        "complete": complete, "targetType": "msg",
        "statusVal": "", "statusType": "auto",
        "x": x, "y": y, "wires": [],
    }


def opcua_endpoint(host: str, port: int, line_id: str) -> dict:
    return {
        "id": nid(), "type": "OpcUa-Endpoint",
        "endpoint": f"opc.tcp://{host}:{port}/line-das/{line_id}",
        "secpol": "None", "secmode": "None",
        "none": True, "login": False,
        "usercert": False, "usercertificate": "", "userprivatekey": "",
    }


def opcua_client(z: str, endpoint_id: str, name: str,
                 x: int, y: int, wires: list) -> dict:
    return {
        "id": nid(), "type": "OpcUa-Client", "z": z,
        "endpoint": endpoint_id,
        "action": "subscribe",
        "deadbandtype": "a", "deadbandvalue": 0,
        "time": 1, "timeUnit": "s",
        "certificate": "n", "localfile": "", "localkeyfile": "",
        "securitymode": "None", "securitypolicy": "None",
        "useTransport": False,
        "maxChunkCount": "1", "maxMessageSize": "8192",
        "maxBufferSize": "8192", "receiveBufferSize": "8192",
        "sendBufferSize": "8192",
        "setstatusandtime": False, "keepsessionalive": True,
        "name": name,
        "x": x, "y": y, "wires": [wires],
    }


# ---------------------------------------------------------------------------
# 라인 1개 컬럼 빌드
# ---------------------------------------------------------------------------

def build_line_column(flow: list[dict], tab_id: str,
                      line_id: str, host: str, y_base: int) -> None:
    port = das_opcua_port(line_id)
    endpoint = opcua_endpoint(host, port, line_id)
    flow.append(endpoint)

    # 1. 라인 헤더 코멘트
    flow.append(comment_node(
        tab_id, f"{line_id}  (opc.tcp://{host}:{port}/line-das/{line_id})",
        info=(
            f"{line_id} 라인 DAS subscribe.\n"
            f"기본 구독: ns=2;s={line_id}.payload (JSON 문자열)\n"
            f"개별 태그 추가: subscribe topics 함수 내 EXTRA_TOPICS 편집"
        ),
        x=200, y=y_base,
    ))

    # 2. 출력단 (오른쪽부터 왼쪽으로 배치 — wire 연결을 위한 순서)
    dbg = debug_node(tab_id, f"{line_id} payload", x=1100, y=y_base + 80,
                     complete="true")
    flow.append(dbg)

    parse = function_node(
        tab_id, f"{line_id} parse",
        PARSE_PAYLOAD_CODE,
        x=900, y=y_base + 80,
        outputs=1, wires=[[dbg["id"]]],
    )
    flow.append(parse)

    client = opcua_client(
        tab_id, endpoint["id"], f"{line_id} subscribe",
        x=680, y=y_base + 80, wires=[parse["id"]],
    )
    flow.append(client)

    split = split_node(tab_id, x=520, y=y_base + 80,
                       wires=[client["id"]])
    flow.append(split)

    topics_fn = function_node(
        tab_id, f"{line_id} topics",
        SUBSCRIBE_TOPICS_CODE_TMPL % {"line_id": line_id},
        x=360, y=y_base + 80,
        outputs=1, wires=[[split["id"]]],
    )
    flow.append(topics_fn)

    flow.append(inject_node(
        tab_id, f"{line_id} subscribe once",
        x=180, y=y_base + 80, wires=[topics_fn["id"]],
        once_delay="2",
    ))


# ---------------------------------------------------------------------------
# 전체 flow 빌드
# ---------------------------------------------------------------------------

def build_flow(lines: list[str], host: str) -> list[dict]:
    tab = tab_node("Host Viewer (DAS subscribe)", info=(
        "도커가 띄운 라인 DAS OPC UA Server 들을 호스트 Node-RED 가 subscribe.\n"
        "단순 조회용 — debug 사이드바에서 라인별 payload 객체를 확인."
    ))
    flow: list[dict] = [tab]

    flow.append(comment_node(
        tab["id"], "Host Viewer — DAS OPC UA subscribe",
        info=(
            f"host: {host}\n"
            f"라인: {', '.join(lines)}\n"
            "각 라인은 ns=2;s=<LINE>.payload 한 노드 구독으로 라인 전체 값 확인 가능.\n"
            "필요하면 각 라인 'topics' 함수 안의 EXTRA_TOPICS 에 노드 추가."
        ),
        x=300, y=30,
    ))

    y = 100
    for line_id in lines:
        build_line_column(flow, tab["id"], line_id, host, y)
        y += 180

    return flow


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="호스트 Node-RED 용 DAS OPC UA subscribe flow 빌더",
    )
    parser.add_argument(
        "--host", default="127.0.0.1",
        help="도커 호스트 주소. 보통 127.0.0.1. 다른 PC 면 그 IP.",
    )
    parser.add_argument(
        "--lines", default="LINE-01,LINE-02,LINE-03",
        help="구독할 라인 목록 (콤마). 기본: LINE-01,LINE-02,LINE-03",
    )
    parser.add_argument(
        "--out", default=str(ROOT / "nodered" / "flows_host_viewer.json"),
        help="출력 파일 경로",
    )
    args = parser.parse_args()

    lines = [s.strip() for s in args.lines.split(",") if s.strip()]
    if not lines:
        raise SystemExit("no lines")

    flow = build_flow(lines, args.host)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(flow, indent=2, ensure_ascii=False),
                   encoding="utf-8")
    print(f"wrote {out} ({len(flow)} nodes) lines={lines} host={args.host}")


if __name__ == "__main__":
    main()
