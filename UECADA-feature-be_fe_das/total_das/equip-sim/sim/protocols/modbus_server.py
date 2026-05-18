"""Modbus TCP 서버 (role 기반, 쓰기 지원).

매핑 규칙 (tag 등장 순서):
- bool  -> coils (FC=1/5/15). 주소 = bool 태그 순번
- int   -> holding registers (FC=3/6/16). 주소 = int 태그 순번 (16bit signed)
- float -> holding registers. 주소 = FLOAT_BASE(=1000) + float 태그 순번*2 (big-endian)

writable 한 태그(power/setpoint) 의 쓰기 함수코드(FC=5/6/15/16) 는
EquipmentState.set_external() 로 전달되어 즉시 반영된다.
read-only 태그는 쓰기 시도해도 무시한다 (값은 다음 tick 에서 다시 덮어쓰임).
"""
from __future__ import annotations

import asyncio
import struct
import threading
from typing import Dict, List, Tuple

from pymodbus.datastore import (
    ModbusSequentialDataBlock,
    ModbusServerContext,
    ModbusSlaveContext,
)
from pymodbus.server import StartAsyncTcpServer

from ..config import SimConfig, TagConfig
from ..log import get_logger
from ..state import EquipmentState

log = get_logger("modbus")
FLOAT_BASE = 1000


def build_mapping(tags: List[TagConfig]):
    """name -> ('coil'|'hr_int'|'hr_float', address)"""
    mapping: Dict[str, Tuple[str, int]] = {}
    addr_to_tag: Dict[Tuple[str, int], str] = {}
    coil_i = int_i = float_i = 0
    for t in tags:
        if t.data_type == "bool":
            mapping[t.name] = ("coil", coil_i)
            addr_to_tag[("coil", coil_i)] = t.name
            coil_i += 1
        elif t.data_type == "int":
            mapping[t.name] = ("hr_int", int_i)
            addr_to_tag[("hr", int_i)] = t.name  # int 1워드
            int_i += 1
        elif t.data_type == "float":
            addr = FLOAT_BASE + float_i * 2
            mapping[t.name] = ("hr_float", addr)
            addr_to_tag[("hr", addr)] = t.name      # hi 워드
            addr_to_tag[("hr", addr + 1)] = t.name  # lo 워드 (같은 태그)
            float_i += 1
    return mapping, addr_to_tag, coil_i, int_i, float_i


def to_int16(v: int) -> int:
    v = max(-32768, min(32767, int(v))) & 0xFFFF
    return v


def float_to_words(v: float) -> Tuple[int, int]:
    hi, lo = struct.unpack(">HH", struct.pack(">f", float(v)))
    return hi, lo


def words_to_float(hi: int, lo: int) -> float:
    return struct.unpack(">f", struct.pack(">HH", hi & 0xFFFF, lo & 0xFFFF))[0]


def s16(v: int) -> int:
    v = int(v) & 0xFFFF
    return v - 0x10000 if v & 0x8000 else v


class WriteAwareSlaveContext(ModbusSlaveContext):
    """클라이언트 쓰기를 가로채 EquipmentState 로 전달."""

    def __init__(self, state: EquipmentState, mapping, addr_to_tag, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._state = state
        self._mapping = mapping
        self._addr_to_tag = addr_to_tag
        # 외부 쓰기 표시용 플래그 (재진입 가드)
        self._internal_write = False

    def _internal_set(self, fx, address, values):
        self._internal_write = True
        try:
            super().setValues(fx, address, values)
        finally:
            self._internal_write = False

    # 외부 클라이언트가 쓰기 요청을 보낼 때 사용하는 FC
    _WRITE_COIL_FCS = {5, 15}      # write_single_coil, write_multi_coils
    _WRITE_HR_FCS = {6, 16, 22, 23}  # write_single_reg, write_multi_regs, mask, rw

    def setValues(self, fx, address, values):
        # 클라이언트 쓰기 요청이면 EquipmentState 에 반영
        if not self._internal_write:
            try:
                if fx in self._WRITE_COIL_FCS:
                    for i, v in enumerate(values):
                        tag = self._addr_to_tag.get(("coil", address + i))
                        if tag:
                            self._state.set_external(tag, bool(v))
                elif fx in self._WRITE_HR_FCS:
                    self._handle_hr_write(address, list(values))
            except Exception as e:
                log.warning("write hook err: %s", e)

        # 데이터스토어 기록 (다음 tick 에서 sensor/counter 만 덮어씀)
        super().setValues(fx, address, values)

    def _handle_hr_write(self, address: int, values: List[int]) -> None:
        i = 0
        while i < len(values):
            addr = address + i
            tag_name = self._addr_to_tag.get(("hr", addr))
            if not tag_name:
                i += 1
                continue
            kind, _ = self._mapping[tag_name]
            if kind == "hr_int":
                self._state.set_external(tag_name, s16(values[i]))
                i += 1
            elif kind == "hr_float":
                if i + 1 < len(values):
                    f = words_to_float(values[i], values[i + 1])
                    self._state.set_external(tag_name, f)
                    i += 2
                else:
                    i += 1
            else:
                i += 1


async def _serve(cfg: SimConfig, state: EquipmentState, stop_event: threading.Event) -> None:
    mapping, addr_to_tag, coil_n, int_n, float_n = build_mapping(cfg.tags)

    hr_size = max(FLOAT_BASE + float_n * 2 + 2, int_n + 2, 16)
    coil_size = max(coil_n + 1, 16)

    slave = WriteAwareSlaveContext(
        state, mapping, addr_to_tag,
        di=ModbusSequentialDataBlock(0, [0] * coil_size),
        co=ModbusSequentialDataBlock(0, [0] * coil_size),
        hr=ModbusSequentialDataBlock(0, [0] * hr_size),
        ir=ModbusSequentialDataBlock(0, [0] * hr_size),
    )
    context = ModbusServerContext(slaves=slave, single=True)

    log.info("=== %s modbus mapping ===", cfg.equipment_name)
    for name, (kind, addr) in mapping.items():
        tag = next(t for t in cfg.tags if t.name == name)
        log.info(
            "  %-22s role=%-8s %-9s @ %d  %s",
            name, tag.role, kind, addr,
            "(RW)" if tag.writable else "(RO)",
        )

    period = cfg.sampling_ms / 1000.0

    def push_to_datastore() -> None:
        """state 의 현재값을 datastore 에 반영 (내부 갱신)."""
        values = state.read_all()
        for name, (kind, addr) in mapping.items():
            v = values[name]
            try:
                if kind == "coil":
                    slave._internal_set(1, addr, [1 if bool(v) else 0])
                elif kind == "hr_int":
                    slave._internal_set(3, addr, [to_int16(v)])
                elif kind == "hr_float":
                    hi, lo = float_to_words(v)
                    slave._internal_set(3, addr, [hi, lo])
            except Exception as e:
                log.warning("datastore set %s err: %s", name, e)

    async def updater() -> None:
        # 처음 한 번 즉시 반영
        push_to_datastore()
        while not stop_event.is_set():
            state.tick()
            push_to_datastore()
            await asyncio.sleep(period)

    log.info("start tcp=%s:%s tags=%d period=%.3fs",
             cfg.host, cfg.port, len(cfg.tags), period)

    server_task = asyncio.create_task(
        StartAsyncTcpServer(context=context, address=(cfg.host, cfg.port))
    )
    updater_task = asyncio.create_task(updater())
    stopper_task = asyncio.create_task(asyncio.to_thread(stop_event.wait))

    _done, pending = await asyncio.wait(
        {server_task, updater_task, stopper_task},
        return_when=asyncio.FIRST_COMPLETED,
    )

    log.info("stopping modbus server")
    for task in pending:
        task.cancel()
        try:
            await task
        except (asyncio.CancelledError, Exception):
            pass


def run(cfg: SimConfig, stop_event: threading.Event) -> None:
    state = EquipmentState(cfg)
    asyncio.run(_serve(cfg, state, stop_event))
