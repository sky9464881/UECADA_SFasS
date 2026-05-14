"""flows_das_*.json 정적 무결성 검사.

- 모든 wires 가 실제 노드 id 를 가리키는지
- modbus-read/modbus-write 의 server, OpcUa-Client 의 endpoint,
  MC Read 의 connection 이 실제 설정 노드를 가리키는지
- function 노드의 JS 코드를 node 로 syntax check (선택, node 설치 시)
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path


def verify(path: Path) -> int:
    data = json.loads(path.read_text(encoding="utf-8"))
    ids: set[str] = {n["id"] for n in data}
    errors: list[str] = []

    # 1. wires 검사
    for n in data:
        wires = n.get("wires") or []
        for out_idx, port in enumerate(wires):
            if not isinstance(port, list):
                continue
            for ref in port:
                if not isinstance(ref, str):
                    errors.append(
                        f"  [wires] {n['type']} {n.get('name', n['id'])} "
                        f"out[{out_idx}]에 비-문자열 ref: {type(ref).__name__}"
                    )
                    continue
                if ref not in ids:
                    errors.append(
                        f"  [wires] {n['type']} {n.get('name', n['id'])} "
                        f"out[{out_idx}] -> 알 수 없는 id {ref}"
                    )

    # 2. config 노드 참조
    REFS = [
        ("modbus-read", "server"),
        ("modbus-write", "server"),
        ("OpcUa-Client", "endpoint"),
        ("MC Read", "connection"),
        ("MC Write", "connection"),
    ]
    for n in data:
        for typ, key in REFS:
            if n["type"] == typ:
                ref = n.get(key)
                if ref and ref not in ids:
                    errors.append(
                        f"  [ref] {typ}.{key} -> 알 수 없는 id {ref}"
                    )

    # 3. 탭(z) 참조 검사
    for n in data:
        z = n.get("z")
        if z and z not in ids:
            errors.append(f"  [tab] {n['type']} z={z} 탭이 없음")

    # 4. function 노드 JS syntax (node 가 있으면)
    js_ok = js_fail = 0
    try:
        subprocess.run(["node", "--version"], check=True, capture_output=True)
        node_ok = True
    except (FileNotFoundError, subprocess.CalledProcessError):
        node_ok = False

    if node_ok:
        for n in data:
            if n["type"] != "function":
                continue
            code = n.get("func", "")
            # function 본문은 함수 안에서 실행되므로 IIFE 로 감싸 syntax 만 확인
            wrapped = f"(async function(msg){{ {code} }})"
            r = subprocess.run(
                ["node", "--check", "-"],
                input=wrapped, text=True, capture_output=True,
            )
            if r.returncode != 0:
                js_fail += 1
                errors.append(f"  [js] {n['name']}: {r.stderr.strip().splitlines()[0]}")
            else:
                js_ok += 1

    print(f"=== {path.name} ===")
    print(f"  nodes: {len(data)}, ids unique: {len(ids) == len(data)}")
    print(f"  js: {js_ok} OK, {js_fail} FAIL (node available: {node_ok})")
    if errors:
        print(f"  ERRORS: {len(errors)}")
        for e in errors[:20]:
            print(e)
        return 1
    print("  ALL OK")
    return 0


def main() -> int:
    here = Path(__file__).parent
    rc = 0
    if len(sys.argv) > 1:
        for p in sys.argv[1:]:
            rc |= verify(Path(p))
        return rc
    # 기본: flows_das.json (단일 라인) + flows_das_*.json (구버전)
    paths = []
    single = here / "flows_das.json"
    if single.exists():
        paths.append(single)
    paths.extend(sorted(here.glob("flows_das_*.json")))
    if not paths:
        print("no flows_das*.json found")
        return 1
    for path in paths:
        rc |= verify(path)
    return rc


if __name__ == "__main__":
    sys.exit(main())
