"""Modbus 서버 (TCP / RTU 양쪽 지원, role 기반).

config 의 각 태그에 박힌 `mb` 매핑(kind / address) 을 그대로 사용한다.
- coil      bool   (FC=1/5/15)
- hr_int    int    (FC=3/6/16, 16bit signed, 1 워드)
- hr_float  float  (FC=3/6/16, big-endian, 2 워드)

writable 한 태그(power/setpoint) 의 쓰기 함수코드(FC=5/6/15/16) 는
EquipmentState.set_external() 로 전달되어 즉시 반영된다.
read-only 태그는 쓰기 시도해도 무시한다 (값은 다음 tick 에서 다시 덮어쓰임).

protocol 별 listener:
- protocol="modbus"          -> Modbus TCP (cfg.host:cfg.port)
- protocol="modbus-rtu"      -> Modbus RTU on serial (cfg.serial_path)
- protocol="modbus-rtu-tcp"  -> RTU 프레임을 TCP 소켓으로 그대로 전송.
                                Moxa NPort 질 시리얼 게이트웨이와 동일한 방식으로,
                                pymodbus 의 StartAsyncTcpServer + Framer.RTU 조합으로 구현.
                                Node-RED 에서는 modbus-client 를
                                clienttype=tcp + tcpType=RTU-BUFFERED 로 설정해서 붙이면 됨.
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
from pymodbus.framer import Framer
from pymodbus.server import StartAsyncTcpServer, StartAsyncSerialServer

from ..config import SimConfig, TagConfig
from ..log import get_logger
from ..state import EquipmentState

log = get_logger("modbus")


def build_mapping(tags: List[TagConfig]):
    """config 에 박힌 mb 매핑을 그대로 ({name -> (kind, address)}, {(kind, addr) -> name}) 로 변환.

    coil:     (kind='coil',     address=N)
    hr_int:   (kind='hr_int',   address=N)
    hr_float: (kind='hr_float', address=N)   # N, N+1 둘 다 점유
    """
    mapping: Dict[str, Tuple[str, int]] = {}
    addr_to_tag: Dict[Tuple[str, int], str] = {}
    coil_max = -1
    hr_max = -1
    for t in tags:
        if t.mb is None:
            raise ValueError(f"tag {t.name} missing mb mapping")
        kind = t.mb.kind
        addr = t.mb.address
        mapping[t.name] = (kind, addr)
        if kind == "coil":
            addr_to_tag[("coil", addr)] = t.name
            coil_max = max(coil_max, addr)
        elif kind == "hr_int":
            addr_to_tag[("hr", addr)] = t.name
            hr_max = max(hr_max, addr)
        elif kind == "hr_float":
            addr_to_tag[("hr", addr)] = t.name
            addr_to_tag[("hr", addr + 1)] = t.name
            hr_max = max(hr_max, addr + 1)
    return mapping, addr_to_tag, coil_max, hr_max


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
        self._internal_write = False

    def _internal_set(self, fx, address, values):
        self._internal_write = True
        try:
            super().setValues(fx, address, values)
        finally:
            self._internal_write = False

    _WRITE_COIL_FCS = {5, 15}
    _WRITE_HR_FCS = {6, 16, 22, 23}

    def setValues(self, fx, address, values):
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


def _build_slave(cfg: SimConfig, state: EquipmentState):
    mapping, addr_to_tag, coil_max, hr_max = build_mapping(cfg.tags)

    hr_size = max(hr_max + 2, 16)
    coil_size = max(coil_max + 2, 16)

    slave = WriteAwareSlaveContext(
        state, mapping, addr_to_tag,
        di=ModbusSequentialDataBlock(0, [0] * coil_size),
        co=ModbusSequentialDataBlock(0, [0] * coil_size),
        hr=ModbusSequentialDataBlock(0, [0] * hr_size),
        ir=ModbusSequentialDataBlock(0, [0] * hr_size),
    )
    return slave, mapping


def _log_mapping(cfg: SimConfig, mapping) -> None:
    log.info("=== %s modbus mapping ===", cfg.equipment_name)
    for name, (kind, addr) in mapping.items():
        tag = next(t for t in cfg.tags if t.name == name)
        log.info(
            "  %-26s role=%-8s %-9s @ %d  %s",
            name, tag.role, kind, addr,
            "(RW)" if tag.writable else "(RO)",
        )


async def _serve(cfg: SimConfig, state: EquipmentState,
                 stop_event: threading.Event) -> None:
    slave, mapping = _build_slave(cfg, state)
    # RTU / RTU-over-TCP 는 slave_id 기반 multi-slave context,
    # 순수 Modbus-TCP 는 single-slave context.
    if cfg.protocol in ("modbus-rtu", "modbus-rtu-tcp"):
        context = ModbusServerContext(slaves={cfg.slave_id: slave}, single=False)
    else:
        context = ModbusServerContext(slaves=slave, single=True)

    _log_mapping(cfg, mapping)

    period = cfg.sampling_ms / 1000.0

    def push_to_datastore() -> None:
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
        push_to_datastore()
        while not stop_event.is_set():
            state.tick()
            push_to_datastore()
            await asyncio.sleep(period)

    if cfg.protocol == "modbus-rtu":
        log.info("start rtu=%s baud=%d parity=%s stopbits=%d slave_id=%d tags=%d period=%.3fs",
                 cfg.serial_path, cfg.baudrate, cfg.parity, cfg.stopbits,
                 cfg.slave_id, len(cfg.tags), period)
        server_task = asyncio.create_task(
            StartAsyncSerialServer(
                context=context,
                port=cfg.serial_path,
                baudrate=cfg.baudrate,
                parity=cfg.parity,
                stopbits=cfg.stopbits,
                bytesize=cfg.bytesize,
                framer=Framer.RTU,
            )
        )
    elif cfg.protocol == "modbus-rtu-tcp":
        # Moxa NPort 스타일: RTU 프레임(unitId+PDU+CRC16) 을 TCP 소켓에
        # 그대로 흠려보낸다. Node-RED 에서는 TCP + RTU-BUFFERED 로 읽으면 됨.
        log.info("start rtu-over-tcp=%s:%s slave_id=%d tags=%d period=%.3fs",
                 cfg.host, cfg.port, cfg.slave_id, len(cfg.tags), period)
        server_task = asyncio.create_task(
            StartAsyncTcpServer(
                context=context,
                address=(cfg.host, cfg.port),
                framer=Framer.RTU,
            )
        )
    else:
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


def run(cfg: SimConfig, state: EquipmentState, stop_event: threading.Event) -> None:
    asyncio.run(_serve(cfg, state, stop_event))
