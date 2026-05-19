"""OPC UA 서버 (role 기반, 쓰기 지원).

- writable (power/setpoint) 변수는 외부 쓰기 가능. 쓰기 이벤트는 EquipmentState 로 전달.
- read-only (sensor/counter/alarm) 변수는 set_writable() 호출하지 않음.
- stddev 등 내부 파라미터는 OPC UA 주소공간에 만들지 않는다.
"""
from __future__ import annotations

import asyncio
import threading
from typing import Any, Dict

from asyncua import Server, ua

from ..config import SimConfig, TagConfig
from ..log import get_logger
from ..state import EquipmentState

log = get_logger("opcua")


def _variant_type(t: TagConfig):
    # OPC UA 일반 클라이언트(node-red-contrib-opcua, UAExpert 등)이
    # 기본으로 보내는 타입에 맞췄서 Int32 / Float 를 쓴다.
    return {
        "float": ua.VariantType.Float,
        "int":   ua.VariantType.Int32,
        "bool":  ua.VariantType.Boolean,
    }[t.data_type]


def _data_type_node(t: TagConfig):
    # 변수의 DataType 속성에 들어갈 NodeId. VariantType 과 일치시킨다.
    return {
        "float": ua.NodeId(ua.ObjectIds.Float),
        "int":   ua.NodeId(ua.ObjectIds.Int32),
        "bool":  ua.NodeId(ua.ObjectIds.Boolean),
    }[t.data_type]


def _coerce_for_state(t: TagConfig, raw: Any) -> Any:
    if t.data_type == "float":
        return float(raw)
    if t.data_type == "int":
        return int(raw)
    if t.data_type == "bool":
        return bool(raw)
    return raw


class WriteHandler:
    """asyncua 0.x/1.x 호환: write_attribute 콜백을 직접 등록하는 대신
    Server 객체에 user manager 를 두기엔 과해서, 폴링 비교 방식으로 처리한다.
    각 writable 변수의 마지막 알려진 값과 비교해 변화가 있으면 외부 쓰기로 간주."""

    def __init__(self, state: EquipmentState, nodes: Dict[str, Any], tag_map: Dict[str, TagConfig]):
        self.state = state
        self.nodes = nodes
        self.tag_map = tag_map
        self._last: Dict[str, Any] = {}

    async def init(self) -> None:
        for name, node in self.nodes.items():
            t = self.tag_map[name]
            if t.writable:
                self._last[name] = await node.read_value()

    async def poll(self) -> None:
        """writable 변수만 폴링해 변화 감지."""
        for name, node in self.nodes.items():
            t = self.tag_map[name]
            if not t.writable:
                continue
            try:
                cur = await node.read_value()
            except Exception:
                continue
            prev = self._last.get(name)
            if cur != prev:
                self.state.set_external(name, _coerce_for_state(t, cur))
                self._last[name] = cur


async def _serve(cfg: SimConfig, state: EquipmentState, stop_event: threading.Event) -> None:
    server = Server()
    await server.init()

    endpoint = f"opc.tcp://{cfg.host}:{cfg.port}/{cfg.equipment_name}/"
    server.set_endpoint(endpoint)
    server.set_server_name(f"EquipSim-{cfg.equipment_name}")

    uri = cfg.namespace or f"urn:equipsim:{cfg.equipment_name}"
    idx = await server.register_namespace(uri)

    # Object NodeId 도 string identifier 로 명시 (ns=<idx>;s=<equipment_name>)
    eq_obj = await server.nodes.objects.add_object(
        ua.NodeId(cfg.equipment_name, idx), cfg.equipment_name,
    )

    nodes: Dict[str, Any] = {}
    tag_map: Dict[str, TagConfig] = {t.name: t for t in cfg.tags}

    log.info("=== %s opcua mapping (endpoint=%s, ns=%d) ===",
             cfg.equipment_name, endpoint, idx)
    initial = state.read_all()
    for t in cfg.tags:
        v = initial[t.name]
        if t.data_type == "bool":
            v = bool(v)
        elif t.data_type == "int":
            v = int(v)
        elif t.data_type == "float":
            v = float(v)

        # NodeId 를 명시적으로 string identifier 로 만든다 -> ns=<idx>;s=<tag_name>
        node_id = ua.NodeId(t.name, idx)
        var = await eq_obj.add_variable(
            node_id, t.name, v,
            varianttype=_variant_type(t),
            datatype=_data_type_node(t),
        )
        if t.writable:
            # asyncua set_writable() 이 AccessLevel + UserAccessLevel 의
            # CurrentWrite 비트를 켜준다 (set_attr_bit 호출).
            await var.set_writable()
        nodes[t.name] = var
        log.info(
            "  %-22s role=%-8s type=%-5s nodeid=ns=%d;s=%s %s",
            t.name, t.role, t.data_type, idx, t.name,
            "(RW)" if t.writable else "(RO)",
        )

    writer = WriteHandler(state, nodes, tag_map)
    period = cfg.sampling_ms / 1000.0

    async with server:
        await writer.init()
        log.info("start tags=%d period=%.3fs", len(cfg.tags), period)

        while not stop_event.is_set():
            # 1) 외부 쓰기 (writable 변수) 가 있었는지 확인 -> state 반영
            await writer.poll()
            # 2) sensor/counter/alarm 재계산
            state.tick()
            # 3) state 의 현재값을 OPC UA 변수에 push
            values = state.read_all()
            for t in cfg.tags:
                v = values[t.name]
                if t.data_type == "bool":
                    v = bool(v)
                elif t.data_type == "int":
                    v = int(v)
                elif t.data_type == "float":
                    v = float(v)
                try:
                    await nodes[t.name].write_value(
                        ua.DataValue(ua.Variant(v, _variant_type(t)))
                    )
                    # writable 변수의 last 도 갱신 (내부 push 가 외부 write 로 오인되지 않도록)
                    if t.writable:
                        writer._last[t.name] = v
                except Exception as e:
                    log.warning("write %s err: %s", t.name, e)

            try:
                await asyncio.wait_for(
                    asyncio.to_thread(stop_event.wait, period),
                    timeout=period + 1.0,
                )
            except asyncio.TimeoutError:
                pass

        log.info("stopping opcua server")


def run(cfg: SimConfig, state: EquipmentState, stop_event: threading.Event) -> None:
    asyncio.run(_serve(cfg, state, stop_event))
