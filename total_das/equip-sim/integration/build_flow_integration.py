"""
통합 DAS Node-RED 플로우 빌더
============================

3개 라인 (LINE-01/02/03) 의 라인-DAS 에 OPC UA Client 로 붙어서
`<line_id>.payload` 변수를 통구독한 뒤,

  1) 디버그 노드 로 stdout 에 통합 페이로드 출력
  2) OPC UA Server (포트 5860, endpoint `integration-das`) 로 재노출

하는 단일 플로우를 만든다.

라인별 라인-DAS 엔드포인트:
    opc.tcp://nodered-line01:4860/line-das/LINE-01
    opc.tcp://nodered-line02:4960/line-das/LINE-02
    opc.tcp://nodered-line03:5060/line-das/LINE-03

이 빌더는 nodered/build_flow_das.py 의 헬퍼들을 그대로 재사용한다.
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

# nodered/ 모듈 임포트를 위해 부모 디렉터리 추가
THIS_DIR = Path(__file__).resolve().parent
PROJ_DIR = THIS_DIR.parent
sys.path.insert(0, str(PROJ_DIR))

from nodered.build_flow_das import (  # noqa: E402
    nid,
    comment_node,
    debug_node,
    function_node,
    make_opcua_endpoint_config,
    make_opcua_server_node,
    opcua_client,
    opcua_item,
    PUBLISH_PAYLOAD_CODE,
)


# ---------------------------------------------------------------------------
# 설정값
# ---------------------------------------------------------------------------

# 통합 DAS 자체가 노출하는 OPC UA Server
INTEGRATION_OPCUA_PORT = 5860
INTEGRATION_OPCUA_ENDPOINT = "integration-das"

# 각 라인 DAS endpoint (factory-net 안에서 컨테이너명으로 접근)
LINE_DAS_ENDPOINTS = [
    # (line_id, host(=container_name), port)
    ("LINE-01", "nodered-line01", 4860),
    ("LINE-02", "nodered-line02", 4960),
    ("LINE-03", "nodered-line03", 5060),
]


# ---------------------------------------------------------------------------
# 플로우 빌더
# ---------------------------------------------------------------------------

def build_integration_flow() -> list[dict]:
    """3개 라인 -> 단일 통합 DAS 플로우.

    구조 (라인별로 같은 줄):

      Inject(1s) -> OpcUa-Item(<line_id>.payload) -> OpcUa-Client(READ)
                       -> Function(parse payload + 라인 식별)
                       -> Debug
                       -> Function(평면 태그 발행 명령 생성) -> OpcUa-Server
    """
    flow: list[dict] = []
    tab_id = nid()

    # 탭
    flow.append({
        "id": tab_id, "type": "tab",
        "label": "통합 DAS — 3개 라인 머지",
        "disabled": False, "info": "",
    })

    # 코멘트 (헤더)
    flow.append(comment_node(
        z=tab_id,
        name="INTEGRATION DAS",
        info=(
            "factory-net 안에서 3개 라인 DAS 의 payload 를 폴링 → "
            "디버그 노드 로 stdout 출력 + OPC UA Server 로 평면 태그 재노출.\n"
            f"  - OPC UA Server: opc.tcp://nodered-das:{INTEGRATION_OPCUA_PORT}/{INTEGRATION_OPCUA_ENDPOINT}\n"
            "  - 변수: ns=2;s=<LINE_ID>.payload (원본 JSON), "
            "ns=2;s=<LINE_ID>.<EQ>.<tag> (평면 태그)"
        ),
        x=150, y=40,
    ))

    # 한 OPC UA Server 노드를 모든 라인 publish 가 공유
    server_node = make_opcua_server_node(
        z=tab_id,
        line_id="INTEGRATION",
        port=INTEGRATION_OPCUA_PORT,
        x=1280, y=200,
    )
    # endpoint 만 통합용으로 덮어쓴다
    server_node["endpoint"] = INTEGRATION_OPCUA_ENDPOINT
    server_node["name"] = f"INTEGRATION DAS Server :{INTEGRATION_OPCUA_PORT}"
    flow.append(server_node)
    server_id = server_node["id"]

    # 라인별 컬럼
    y_step = 140
    for idx, (line_id, host, port) in enumerate(LINE_DAS_ENDPOINTS):
        y_base = 120 + idx * y_step

        # 엔드포인트 config (전역 노드)
        endpoint_id = nid()
        endpoint = make_opcua_endpoint_config(endpoint_id, host, port)
        # endpoint URL 에 path 까지 정확히 박는다 (라인-DAS 가 endpoint=line-das/<id> 로 띄움)
        endpoint["endpoint"] = f"opc.tcp://{host}:{port}/line-das/{line_id}"
        flow.append(endpoint)

        # 코멘트
        flow.append(comment_node(
            z=tab_id,
            name=f"--- {line_id} ---",
            info=endpoint["endpoint"],
            x=150, y=y_base - 30,
        ))

        # 1초 트리거
        inject_node = {
            "id": nid(), "type": "inject", "z": tab_id,
            "name": f"1s tick {line_id}",
            "props": [{"p": "payload"}],
            "repeat": "1", "crontab": "", "once": True, "onceDelay": 0.5,
            "topic": "", "payload": "", "payloadType": "date",
            "x": 160, "y": y_base, "wires": [[]],
        }
        # 아래에서 wires 연결
        flow.append(inject_node)

        # OpcUa-Item: <line_id>.payload (String)
        item_id = nid()
        item_node = {
            "id": item_id, "type": "OpcUa-Item", "z": tab_id,
            "item": f"ns=2;s={line_id}.payload",
            "datatype": "String", "value": "",
            "name": f"{line_id}.payload",
            "x": 380, "y": y_base, "wires": [[]],
        }
        flow.append(item_node)
        inject_node["wires"] = [[item_id]]

        # OpcUa-Client: READ
        client_id = nid()
        client_node = opcua_client(
            z=tab_id,
            endpoint_id=endpoint_id,
            action="read",
            name=f"READ {line_id}",
            x=600, y=y_base,
            wires=[],
        )
        client_node["id"] = client_id
        flow.append(client_node)
        item_node["wires"] = [[client_id]]

        # Function: payload(String JSON) 파싱 + msg.payload 로 교체
        parse_id = nid()
        parse_code = f"""\
// {line_id} OPC UA Client READ 결과 → JSON 파싱
// msg.payload 는 OpcUa-Item 의 String 값 (line-DAS 의 EMIT 결과)
const raw = msg.payload;
if (raw === null || raw === undefined || raw === "") return null;
let obj;
try {{
    obj = (typeof raw === 'string') ? JSON.parse(raw) : raw;
}} catch (e) {{
    node.warn(`{line_id} parse error: ${{e.message}}`);
    return null;
}}
if (!obj || !obj.line_id) {{
    obj = obj || {{}};
    obj.line_id = "{line_id}";
}}
msg.payload = obj;
msg.topic = obj.line_id;
return msg;
"""
        parse_node = function_node(
            z=tab_id,
            name=f"PARSE {line_id}",
            code=parse_code,
            x=820, y=y_base,
            wires=[],
            outputs=1,
        )
        parse_node["id"] = parse_id
        flow.append(parse_node)
        client_node["wires"] = [[parse_id]]

        # Debug 노드 (stdout 으로 통합 payload 출력)
        debug_id = nid()
        dbg = debug_node(
            z=tab_id,
            name=f"DEBUG {line_id}",
            x=1060, y=y_base - 40,
            to_console=True,
        )
        dbg["id"] = debug_id
        flow.append(dbg)

        # Publish 함수 (평면 태그 발행) -> OpcUa-Server
        pub_id = nid()
        pub_node = function_node(
            z=tab_id,
            name=f"PUBLISH {line_id}",
            code=PUBLISH_PAYLOAD_CODE,
            x=1060, y=y_base + 40,
            wires=[[server_id]],
            outputs=1,
        )
        pub_node["id"] = pub_id
        flow.append(pub_node)

        # parse -> debug, publish 둘 다
        parse_node["wires"] = [[debug_id, pub_id]]

    return flow


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def main() -> None:
    flow = build_integration_flow()
    out = THIS_DIR / "flows_integration.json"
    out.write_text(json.dumps(flow, ensure_ascii=False, indent=2),
                   encoding="utf-8")
    types: dict[str, int] = {}
    for n in flow:
        t = n.get("type", "?")
        types[t] = types.get(t, 0) + 1
    summary = ", ".join(f"{t}={c}" for t, c in sorted(types.items()))
    print(f"wrote {out} ({len(flow)} nodes) — {summary}")


if __name__ == "__main__":
    main()
