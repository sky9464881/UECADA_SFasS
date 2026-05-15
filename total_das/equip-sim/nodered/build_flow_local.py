"""로컬 실행 환경용 Node-RED flow 생성기.

Docker 컨테이너명 대신 127.0.0.1 + 로컬 포트를 사용.
"""
import json, os, sys

# 같은 디렉토리의 build_flow 를 재사용하기 위해 일단 그것의 결과를 읽어 패치
this = os.path.dirname(__file__)
sys.path.insert(0, this)

# 빌드 후 결과 파일을 패치
import subprocess
subprocess.check_call([sys.executable, os.path.join(this, "build_flow.py")])

src = os.path.join(this, "flows_verify_v2.json")
dst = os.path.join(this, "flows_verify_local.json")

flow = json.load(open(src))

# Docker 호스트 매핑과 동일
port_map = {
    "casting_01":     5021,
    "washing_01":     5031,
    "assembly_01":    5041,
    "assembly_02":    5042,
    "inspection_01":  5051,
    "inspection_02":  5052,
    "machining_01":   4841,
    "machining_02":   4842,
    "machining_03":   4843,
}

# modbus-client: tcpHost -> 127.0.0.1, tcpPort -> 호스트 포트
# OpcUa-Endpoint: endpoint URL 의 host:port 부분만 변경
for n in flow:
    if n.get("type") == "modbus-client":
        name = n.get("name")
        if name in port_map:
            n["tcpHost"] = "127.0.0.1"
            n["tcpPort"] = str(port_map[name])
    elif n.get("type") == "OpcUa-Endpoint":
        name = n.get("name")
        if name in port_map:
            n["endpoint"] = f"opc.tcp://127.0.0.1:{port_map[name]}/{name}/"

# tab label 만 표시 변경
for n in flow:
    if n.get("type") == "tab":
        n["label"] = "Equip Sim Verify (LOCAL)"
        n["info"] = "127.0.0.1 + 로컬 포트로 9대 설비 검증"

json.dump(flow, open(dst, "w"), indent=2)
print(f"wrote {dst} (nodes: {len(flow)})")
