"""설비의 모든 태그 현재값을 보관하는 공유 상태.

- 외부(Modbus/OPC UA) 쓰기는 set_external() 로 들어와 writable 태그만 반영
- tick() 마다 sensor/counter/alarm 값을 재계산
- read_all() 로 현재 외부 노출 값 dict 반환 (stddev 등 내부값 제외)
"""
from __future__ import annotations

import random
import threading
from typing import Any, Dict

from .config import SimConfig, TagConfig
from .log import get_logger

log = get_logger("state")


class EquipmentState:
    def __init__(self, cfg: SimConfig) -> None:
        self.cfg = cfg
        self._lock = threading.Lock()

        # 모든 태그의 현재값
        self._values: Dict[str, Any] = {}
        for t in cfg.tags:
            self._values[t.name] = self._initial(t)

        # power 태그 이름 (정확히 1개임이 검증됨)
        self._power_name = next(t.name for t in cfg.tags if t.role == "power")

        # name -> TagConfig
        self._by_name: Dict[str, TagConfig] = {t.name: t for t in cfg.tags}

    @staticmethod
    def _initial(t: TagConfig) -> Any:
        if t.data_type == "bool":
            return bool(t.base_value)
        if t.data_type == "int":
            return int(t.base_value)
        if t.data_type == "float":
            return float(t.base_value)
        return t.base_value

    # ---------- 외부 쓰기 ----------
    def set_external(self, name: str, value: Any) -> bool:
        """외부에서 들어온 쓰기 요청. writable 한 태그만 받는다."""
        t = self._by_name.get(name)
        if not t:
            log.warning("write reject: unknown tag '%s'", name)
            return False
        if not t.writable:
            log.warning("write reject: '%s' is read-only (role=%s)", name, t.role)
            return False
        coerced = self._coerce(t, value)
        with self._lock:
            old = self._values[name]
            self._values[name] = coerced
        log.info("write '%s' %s -> %s", name, old, coerced)
        return True

    @staticmethod
    def _coerce(t: TagConfig, value: Any) -> Any:
        if t.data_type == "bool":
            if isinstance(value, (int, float)):
                return bool(value)
            if isinstance(value, str):
                return value.strip().lower() in ("1", "true", "on", "y")
            return bool(value)
        if t.data_type == "int":
            return int(round(float(value)))
        if t.data_type == "float":
            return float(value)
        return value

    # ---------- 주기 갱신 ----------
    def tick(self) -> None:
        with self._lock:
            power_on = bool(self._values[self._power_name])

            for t in self.cfg.tags:
                if t.role in ("power", "setpoint"):
                    # 외부 입력값을 보존
                    continue

                if not power_on:
                    # off 시 강제 0/false
                    self._values[t.name] = False if t.data_type == "bool" else 0
                    continue

                if t.role == "sensor":
                    if t.source_sp:
                        center = float(self._values.get(t.source_sp, 0))
                    else:
                        center = float(t.base_value or 0.0)
                    noise = random.gauss(0.0, float(t.stddev or 0.0))
                    val = center + noise
                    if t.data_type == "int":
                        val = int(round(val))
                    elif t.data_type == "bool":
                        # bool sensor 는 stddev 0 이 기본 (명세상 RO bool)
                        val = bool(center)
                    self._values[t.name] = val

                elif t.role == "counter":
                    cur = int(self._values[t.name])
                    self._values[t.name] = cur + int(t.step)

                elif t.role == "alarm":
                    # 그대로 유지 (base_value 기반)
                    pass

    # ---------- 읽기 ----------
    def read_all(self) -> Dict[str, Any]:
        """외부 노출 값. stddev 등 내부 파라미터는 포함하지 않는다."""
        with self._lock:
            return {t.name: self._values[t.name] for t in self.cfg.tags}

    def read(self, name: str) -> Any:
        with self._lock:
            return self._values.get(name)
