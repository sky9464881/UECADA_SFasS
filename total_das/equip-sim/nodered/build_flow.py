"""9대 설비 검증 Node-RED flow 생성기 (읽기 + 쓰기 시나리오 포함)."""
import json, os, uuid

def nid(p=""): return (p + uuid.uuid4().hex)[:24]

CFG = os.path.join(os.path.dirname(__file__), "..", "configs")
TAB = "tab_verify_v2"

nodes = [{
    "id": TAB, "type": "tab", "label": "Equip Sim Verify v2",
    "disabled": False,
    "info": "role 기반 설비 시뮬 검증: 읽기/쓰기/power off"
}]

# ---- Modbus 설비 ----
modbus_servers = [
    "casting_01", "washing_01",
    "assembly_01", "assembly_02",
    "inspection_01", "inspection_02",
]

def load_tags(name):
    return json.load(open(os.path.join(CFG, f"{name}.json")))["tags"]

def split_addrs(tags):
    """tag 등장 순서대로 매핑 계산."""
    coil_i = int_i = float_i = 0
    coils, ints, floats = [], [], []
    for t in tags:
        if t["data_type"] == "bool":
            coils.append((t["name"], t["role"], coil_i)); coil_i += 1
        elif t["data_type"] == "int":
            ints.append((t["name"], t["role"], int_i)); int_i += 1
        elif t["data_type"] == "float":
            floats.append((t["name"], t["role"], 1000 + float_i * 2)); float_i += 1
    return coils, ints, floats

# 전역 트리거
trig = {
    "id": nid("trg_"), "type": "inject", "z": TAB, "name": "read tick 2s",
    "props": [{"p": "payload"}], "repeat": "2", "once": True, "onceDelay": "4",
    "topic": "", "payload": "", "payloadType": "date",
    "x": 130, "y": 60, "wires": [[]],
}
nodes.append(trig)

# 공용 collector
collector_id = nid("col_")
nodes.append({
    "id": collector_id, "type": "function", "z": TAB,
    "name": "collect 9-equipments",
    "func": (
        "const eq = msg.topic;\n"
        "if (!eq) return null;\n"
        "const store = flow.get('store') || {};\n"
        "store[eq] = Object.assign(store[eq] || {}, msg.payload);\n"
        "flow.set('store', store);\n"
        "if (Object.keys(store).length >= 9) {\n"
        "  msg.topic = 'ALL';\n"
        "  msg.payload = store;\n"
        "  flow.set('store', {});\n"
        "  return msg;\n"
        "}\n"
        "return null;\n"
    ),
    "outputs": 1, "x": 1100, "y": 400, "wires": [[]],
})

dbg_all_id = nid("dbg_")
nodes.append({
    "id": dbg_all_id, "type": "debug", "z": TAB,
    "name": "ALL EQUIPMENT", "active": True, "tosidebar": True,
    "complete": "payload", "targetType": "msg",
    "x": 1330, "y": 400, "wires": [],
})
nodes[-2]["wires"] = [[dbg_all_id]]

# ---- Modbus 설비별 노드 ----
y = 100
for eq in modbus_servers:
    tags = load_tags(eq)
    coils, ints, floats = split_addrs(tags)

    server_id = nid("mb_")
    nodes.append({
        "id": server_id, "type": "modbus-client",
        "name": eq, "clienttype": "tcp",
        "bufferCommands": True, "stateLogEnabled": False, "queueLogEnabled": False,
        "tcpHost": eq, "tcpPort": "502", "tcpType": "DEFAULT",
        "unit_id": "1", "commandDelay": "1", "clientTimeout": "2000",
        "reconnectOnTimeout": True, "reconnectTimeout": "2000",
        "parallelUnitIdsAllowed": True,
    })

    base_x = 280

    # --- coil read ---
    rd_co = nid("rdc_")
    nodes.append({
        "id": rd_co, "type": "modbus-read", "z": TAB,
        "name": f"{eq} coils", "topic": "",
        "showStatusActivities": False, "logIOActivities": False, "showErrors": True,
        "unitid": "", "dataType": "Coil", "adr": "0", "quantity": str(max(len(coils), 1)),
        "rate": "2", "rateUnit": "s",
        "delayOnStart": False, "startDelayTime": "",
        "server": server_id, "useIOFile": False, "useIOForPayload": False,
        "emptyMsgOnFail": False,
        "x": base_x, "y": y, "wires": [[], []],
    })
    fn_co = nid("fnc_")
    nodes.append({
        "id": fn_co, "type": "function", "z": TAB,
        "name": f"{eq} coils->json",
        "func": (
            f"const names = {json.dumps([c[0] for c in coils])};\n"
            "const bits = Array.isArray(msg.payload) ? msg.payload : (msg.payload.data || []);\n"
            "const out = {};\n"
            "names.forEach((n,i) => out[n] = !!bits[i]);\n"
            f"msg.topic = '{eq}';\n"
            "msg.payload = out;\n"
            "return msg;\n"
        ),
        "outputs": 1, "x": base_x + 230, "y": y, "wires": [[collector_id]],
    })
    nodes[-2]["wires"] = [[fn_co], []]

    # --- int read ---
    rd_int = nid("rdi_")
    nodes.append({
        "id": rd_int, "type": "modbus-read", "z": TAB,
        "name": f"{eq} ints", "topic": "",
        "showStatusActivities": False, "logIOActivities": False, "showErrors": True,
        "unitid": "", "dataType": "HoldingRegister",
        "adr": "0", "quantity": str(max(len(ints), 1)),
        "rate": "2", "rateUnit": "s",
        "delayOnStart": False, "startDelayTime": "",
        "server": server_id, "useIOFile": False, "useIOForPayload": False,
        "emptyMsgOnFail": False,
        "x": base_x, "y": y + 35, "wires": [[], []],
    })
    fn_int = nid("fni_")
    nodes.append({
        "id": fn_int, "type": "function", "z": TAB,
        "name": f"{eq} ints->json",
        "func": (
            f"const names = {json.dumps([i[0] for i in ints])};\n"
            "const regs = Array.isArray(msg.payload) ? msg.payload : (msg.payload.data || []);\n"
            "function s16(v){return (v&0x8000)?v-0x10000:v;}\n"
            "const out = {};\n"
            "names.forEach((n,i) => out[n] = s16(regs[i]||0));\n"
            f"msg.topic = '{eq}';\n"
            "msg.payload = out;\n"
            "return msg;\n"
        ),
        "outputs": 1, "x": base_x + 230, "y": y + 35, "wires": [[collector_id]],
    })
    nodes[-2]["wires"] = [[fn_int], []]

    # --- float read ---
    rd_fl = nid("rdf_")
    nodes.append({
        "id": rd_fl, "type": "modbus-read", "z": TAB,
        "name": f"{eq} floats", "topic": "",
        "showStatusActivities": False, "logIOActivities": False, "showErrors": True,
        "unitid": "", "dataType": "HoldingRegister",
        "adr": "1000", "quantity": str(max(len(floats) * 2, 2)),
        "rate": "2", "rateUnit": "s",
        "delayOnStart": False, "startDelayTime": "",
        "server": server_id, "useIOFile": False, "useIOForPayload": False,
        "emptyMsgOnFail": False,
        "x": base_x, "y": y + 70, "wires": [[], []],
    })
    fn_fl = nid("fnf_")
    nodes.append({
        "id": fn_fl, "type": "function", "z": TAB,
        "name": f"{eq} floats->json",
        "func": (
            f"const names = {json.dumps([f[0] for f in floats])};\n"
            "const regs = Array.isArray(msg.payload) ? msg.payload : (msg.payload.data || []);\n"
            "function f(i){const b=Buffer.alloc(4);"
            "b.writeUInt16BE(regs[i*2]&0xFFFF,0);"
            "b.writeUInt16BE(regs[i*2+1]&0xFFFF,2);"
            "return b.readFloatBE(0);}\n"
            "const out = {};\n"
            "names.forEach((n,i) => out[n] = Number(f(i).toFixed(3)));\n"
            f"msg.topic = '{eq}';\n"
            "msg.payload = out;\n"
            "return msg;\n"
        ),
        "outputs": 1, "x": base_x + 230, "y": y + 70, "wires": [[collector_id]],
    })
    nodes[-2]["wires"] = [[fn_fl], []]

    trig["wires"][0].extend([rd_co, rd_int, rd_fl])
    y += 140

# ---- OPC UA 설비 ----
opc_y = 100
opc_eps = [
    "machining_01", "machining_02", "machining_03",
]

for eq in opc_eps:
    tags = load_tags(eq)
    ep_id = nid("opc_ep_")
    nodes.append({
        "id": ep_id, "type": "OpcUa-Endpoint",
        "endpoint": f"opc.tcp://{eq}:4840/{eq}/",
        "secpol": "None", "secmode": "None",
        "none": True, "login": False, "usercert": False,
        "name": eq,
    })

    inj = nid("opc_inj_")
    nodes.append({
        "id": inj, "type": "inject", "z": TAB,
        "name": f"{eq} tick",
        "props": [{"p": "payload"}],
        "repeat": "2", "once": True, "onceDelay": "5",
        "payloadType": "date",
        "x": 130, "y": opc_y, "wires": [[]],
    })

    item_ids = []
    for j, t in enumerate(tags):
        dtype = {"float": "Double", "int": "Int64", "bool": "Boolean"}[t["data_type"]]
        it = nid("opc_it_")
        item_ids.append(it)
        nodes.append({
            "id": it, "type": "OpcUa-Item", "z": TAB,
            "item": f"ns=2;s={t['name']}",
            "datatype": dtype, "value": "",
            "name": f"{eq}.{t['name']}",
            "x": 330, "y": opc_y + j * 22, "wires": [[]],
        })

    cli = nid("opc_cli_")
    nodes.append({
        "id": cli, "type": "OpcUa-Client", "z": TAB,
        "endpoint": ep_id, "action": "read",
        "deadbandtype": "a", "deadbandvalue": 1,
        "time": 1, "timeUnit": "s",
        "certificate": "n", "localfile": "", "localkeyfile": "",
        "securitymode": "None", "securitypolicy": "None",
        "name": f"{eq} read",
        "x": 600, "y": opc_y + 60, "wires": [[]],
    })

    tag_names = [t["name"] for t in tags]
    merger = nid("opc_mg_")
    nodes.append({
        "id": merger, "type": "function", "z": TAB,
        "name": f"{eq} merge",
        "func": (
            f"const names = {json.dumps(tag_names)};\n"
            "const k = (msg.browseName || msg.topic || '').replace(/^.*[:.]/, '');\n"
            f"const key = 'opc_{eq}';\n"
            "const ctx = flow.get(key) || {};\n"
            "if (k && names.includes(k)) ctx[k] = msg.payload;\n"
            "flow.set(key, ctx);\n"
            "if (Object.keys(ctx).length >= names.length) {\n"
            f"  msg.topic = '{eq}';\n"
            "  msg.payload = ctx;\n"
            "  flow.set(key, {});\n"
            "  return msg;\n"
            "}\n"
            "return null;\n"
        ),
        "outputs": 1, "x": 830, "y": opc_y + 60, "wires": [[collector_id]],
    })

    # wire
    for n in nodes:
        if n["id"] == inj: n["wires"] = [item_ids[:]]
        if n["id"] in item_ids: n["wires"] = [[cli]]
        if n["id"] == cli: n["wires"] = [[merger]]

    opc_y += max(len(tags) * 22 + 60, 280)

# 트리거 wire 중복 제거
trig["wires"][0] = list(dict.fromkeys(trig["wires"][0]))

# =================================================================
# 쓰기 시나리오 노드들 (별도 그룹)
# =================================================================
sw_y = 1100

# --- 시나리오 1: casting_01.melt_temp_sp 를 900 으로 쓰기 (Modbus FC=16) ---
inj_sp = nid("inj_sp_")
nodes.append({
    "id": inj_sp, "type": "inject", "z": TAB,
    "name": "▶ casting_01.melt_temp_sp = 900",
    "props": [{"p": "payload"}, {"p": "topic", "vt": "str"}],
    "repeat": "", "once": False,
    "topic": "", "payload": "900", "payloadType": "num",
    "x": 200, "y": sw_y, "wires": [[]],
})
fn_sp_build = nid("fn_sp_")
nodes.append({
    "id": fn_sp_build, "type": "function", "z": TAB,
    "name": "build float write",
    "func": (
        "// float 32 -> 2 words big-endian\n"
        "const f = Number(msg.payload);\n"
        "const buf = Buffer.alloc(4);\n"
        "buf.writeFloatBE(f, 0);\n"
        "const hi = buf.readUInt16BE(0);\n"
        "const lo = buf.readUInt16BE(2);\n"
        "msg.payload = {\n"
        "  value: [hi, lo],\n"
        "  fc: 16, unitid: 1, address: 1000, quantity: 2\n"
        "};\n"
        "return msg;\n"
    ),
    "outputs": 1, "x": 470, "y": sw_y, "wires": [[]],
})

# modbus-flex-write 를 사용 (FC 동적)
wr_sp = nid("wr_sp_")
# casting_01 의 server id 를 찾는다
casting_server = next(n["id"] for n in nodes if n.get("type") == "modbus-client" and n.get("name") == "casting_01")
nodes.append({
    "id": wr_sp, "type": "modbus-flex-write", "z": TAB,
    "name": "write casting_01 (melt_temp_sp)",
    "showStatusActivities": False, "showErrors": True,
    "server": casting_server,
    "emptyMsgOnFail": False, "keepMsgProperties": False,
    "x": 800, "y": sw_y, "wires": [[], []],
})
nodes[-3]["wires"] = [[fn_sp_build]]  # inject -> fn
nodes[-2]["wires"] = [[wr_sp]]        # fn    -> write

# --- 시나리오 2: casting_01.power 토글 (FC=5) ---
inj_off = nid("inj_off_")
nodes.append({
    "id": inj_off, "type": "inject", "z": TAB,
    "name": "▶ casting_01.power = OFF",
    "props": [{"p": "payload"}], "repeat": "", "once": False,
    "payload": "false", "payloadType": "bool",
    "x": 200, "y": sw_y + 60, "wires": [[]],
})
inj_on = nid("inj_on_")
nodes.append({
    "id": inj_on, "type": "inject", "z": TAB,
    "name": "▶ casting_01.power = ON",
    "props": [{"p": "payload"}], "repeat": "", "once": False,
    "payload": "true", "payloadType": "bool",
    "x": 200, "y": sw_y + 100, "wires": [[]],
})
fn_pw = nid("fn_pw_")
nodes.append({
    "id": fn_pw, "type": "function", "z": TAB,
    "name": "build coil write",
    "func": (
        "msg.payload = {\n"
        "  value: !!msg.payload,\n"
        "  fc: 5, unitid: 1, address: 0, quantity: 1\n"
        "};\n"
        "return msg;\n"
    ),
    "outputs": 1, "x": 470, "y": sw_y + 80, "wires": [[]],
})
wr_pw = nid("wr_pw_")
nodes.append({
    "id": wr_pw, "type": "modbus-flex-write", "z": TAB,
    "name": "write casting_01 (power)",
    "showStatusActivities": False, "showErrors": True,
    "server": casting_server,
    "emptyMsgOnFail": False, "keepMsgProperties": False,
    "x": 800, "y": sw_y + 80, "wires": [[], []],
})
# wire
for n in nodes:
    if n["id"] in (inj_off, inj_on): n["wires"] = [[fn_pw]]
    if n["id"] == fn_pw: n["wires"] = [[wr_pw]]

# --- 시나리오 3: OPC UA machining_01.spindle_temp_sp = 80 ---
inj_opc = nid("inj_opc_")
nodes.append({
    "id": inj_opc, "type": "inject", "z": TAB,
    "name": "▶ machining_01.spindle_temp_sp = 80",
    "props": [{"p": "payload"}], "repeat": "", "once": False,
    "payload": "80", "payloadType": "num",
    "x": 200, "y": sw_y + 160, "wires": [[]],
})

# OPC UA 쓰기는 OpcUa-Item + OpcUa-Client(write) 조합
ep_mach1 = next(n["id"] for n in nodes if n.get("type") == "OpcUa-Endpoint" and n.get("name") == "machining_01")
item_w = nid("opc_iw_")
nodes.append({
    "id": item_w, "type": "OpcUa-Item", "z": TAB,
    "item": "ns=2;s=spindle_temp_sp",
    "datatype": "Double", "value": "",
    "name": "spindle_temp_sp",
    "x": 470, "y": sw_y + 160, "wires": [[]],
})
cli_w = nid("opc_clw_")
nodes.append({
    "id": cli_w, "type": "OpcUa-Client", "z": TAB,
    "endpoint": ep_mach1, "action": "write",
    "deadbandtype": "a", "deadbandvalue": 1,
    "time": 1, "timeUnit": "s",
    "certificate": "n", "localfile": "", "localkeyfile": "",
    "securitymode": "None", "securitypolicy": "None",
    "name": "write machining_01",
    "x": 800, "y": sw_y + 160, "wires": [[]],
})
for n in nodes:
    if n["id"] == inj_opc: n["wires"] = [[item_w]]
    if n["id"] == item_w: n["wires"] = [[cli_w]]

# --- 시나리오 4: machining_01.power OFF/ON ---
inj_mpw_off = nid("inj_mpwo_")
inj_mpw_on  = nid("inj_mpwn_")
nodes.append({
    "id": inj_mpw_off, "type": "inject", "z": TAB,
    "name": "▶ machining_01.power = OFF",
    "props": [{"p": "payload"}], "repeat": "", "once": False,
    "payload": "false", "payloadType": "bool",
    "x": 200, "y": sw_y + 220, "wires": [[]],
})
nodes.append({
    "id": inj_mpw_on, "type": "inject", "z": TAB,
    "name": "▶ machining_01.power = ON",
    "props": [{"p": "payload"}], "repeat": "", "once": False,
    "payload": "true", "payloadType": "bool",
    "x": 200, "y": sw_y + 260, "wires": [[]],
})
item_mpw = nid("opc_imp_")
nodes.append({
    "id": item_mpw, "type": "OpcUa-Item", "z": TAB,
    "item": "ns=2;s=power",
    "datatype": "Boolean", "value": "",
    "name": "machining_01.power",
    "x": 470, "y": sw_y + 240, "wires": [[]],
})
cli_mpw = nid("opc_clmp_")
nodes.append({
    "id": cli_mpw, "type": "OpcUa-Client", "z": TAB,
    "endpoint": ep_mach1, "action": "write",
    "deadbandtype": "a", "deadbandvalue": 1,
    "time": 1, "timeUnit": "s",
    "certificate": "n", "localfile": "", "localkeyfile": "",
    "securitymode": "None", "securitypolicy": "None",
    "name": "write machining_01 power",
    "x": 800, "y": sw_y + 240, "wires": [[]],
})
for n in nodes:
    if n["id"] in (inj_mpw_off, inj_mpw_on): n["wires"] = [[item_mpw]]
    if n["id"] == item_mpw: n["wires"] = [[cli_mpw]]

# Comment node 로 그룹 설명
nodes.append({
    "id": nid("c1_"), "type": "comment", "z": TAB,
    "name": "==== 읽기 (Modbus 6대 + OPC UA 3대) ====",
    "info": "2초마다 9대 설비 전체 값을 한 객체로 모아 debug",
    "x": 230, "y": 30,
})
nodes.append({
    "id": nid("c2_"), "type": "comment", "z": TAB,
    "name": "==== 쓰기 시나리오 (버튼 눌러서 실행) ====",
    "info": "각 inject 좌측 버튼을 누르면 해당 명령이 설비로 전송된다.\n"
            "ALL EQUIPMENT debug 에서 변화를 확인.",
    "x": 250, "y": sw_y - 50,
})

out = os.path.join(os.path.dirname(__file__), "flows_verify_v2.json")
with open(out, "w") as f:
    json.dump(nodes, f, indent=2)
print("wrote", out, "nodes:", len(nodes))
