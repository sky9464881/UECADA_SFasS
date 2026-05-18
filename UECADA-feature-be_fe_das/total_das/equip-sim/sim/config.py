"""Config loader (role 기반 스키마).

role 종류:
- power     : 공통 1개. bool. 외부에서 읽기/쓰기 가능.
- setpoint  : 목표값. 외부 읽기/쓰기. sensor 의 기준값으로 쓰임.
- sensor    : 외부 읽기 전용. source_sp 의 현재값 + stddev 노이즈로 생성.
- counter   : 외부 읽기 전용. base_value 부터 step 씩 증가.
- alarm     : 외부 읽기 전용. base_value 그대로.

power == 0 (off) 일 때 sensor/counter/alarm 은 모두 0/false 로 강제된다.
stddev 는 외부(Modbus/OPC UA) 에 노출되지 않는 내부 노이즈 파라미터다.
"""
from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, List, Optional


ROLE_WRITABLE = {"power", "setpoint"}
ROLE_READABLE_EXTERNAL = {"power", "setpoint", "sensor", "counter", "alarm"}
ROLES = {"power", "setpoint", "sensor", "counter", "alarm"}
DTYPES = {"int", "float", "bool"}


# device code -> (1-byte code, is_bit)  -- MCMapping 이 참조하므로 앞에 정의
MC_DEVICE_CODES = {
    "M": (0x90, True),   # internal relay  (bit)
    "X": (0x9C, True),   # input           (bit)
    "Y": (0x9D, True),   # output          (bit)
    "D": (0xA8, False),  # data register   (word)
    "R": (0xAF, False),  # file register   (word)
    "W": (0xB4, False),  # link register   (word)
}
MC_BIT_DEVICES = {k for k, (_c, b) in MC_DEVICE_CODES.items() if b}
MC_WORD_DEVICES = {k for k, (_c, b) in MC_DEVICE_CODES.items() if not b}


@dataclass
class MCMapping:
    """MC Protocol 용 디바이스 매핑.

    device: 'D' (word reg), 'M' (bit), 'R' (file reg) 등
    address: PLC 내부 주소 (10진)
    bool  -> 1 bit
    int   -> 1 word (signed 16bit)
    float -> 2 word (little-endian, IEEE-754)
    """
    device: str
    address: int

    def __post_init__(self) -> None:
        if self.device not in MC_DEVICE_CODES:
            raise ValueError(
                f"invalid mc device '{self.device}' "
                f"(supported: {sorted(MC_DEVICE_CODES)})"
            )
        if not isinstance(self.address, int) or self.address < 0:
            raise ValueError(f"mc address must be non-negative int, got {self.address!r}")


@dataclass
class TagConfig:
    name: str
    role: str
    data_type: str
    base_value: Any = 0          # power / setpoint / counter / alarm 에서 사용
    stddev: float = 0.0          # sensor 에서만 사용 (외부 미노출)
    source_sp: Optional[str] = None  # sensor 가 참조하는 setpoint 이름
    step: int = 1                # counter 에서만 사용
    mc: Optional[MCMapping] = None   # MC Protocol 프로토콜 용 매핑

    def __post_init__(self) -> None:
        if self.role not in ROLES:
            raise ValueError(f"invalid role: {self.role} ({self.name})")
        if self.data_type not in DTYPES:
            raise ValueError(f"invalid data_type: {self.data_type} ({self.name})")
        if self.role == "power" and self.data_type != "bool":
            raise ValueError(f"power role must be bool ({self.name})")
        if self.role == "sensor" and not self.source_sp and self.base_value in (None, ""):
            raise ValueError(
                f"sensor must define source_sp or base_value ({self.name})"
            )
        if self.role == "counter" and self.data_type != "int":
            raise ValueError(f"counter must be int ({self.name})")

        # mc 가 dict 로 와있으면 MCMapping 으로 변환
        if isinstance(self.mc, dict):
            self.mc = MCMapping(**self.mc)
        if self.mc is not None:
            # bool 은 bit device, int/float 는 word device 로만 허용
            if self.data_type == "bool" and self.mc.device not in MC_BIT_DEVICES:
                raise ValueError(
                    f"tag '{self.name}': bool data_type requires bit device "
                    f"({sorted(MC_BIT_DEVICES)}), got {self.mc.device}"
                )
            if self.data_type in ("int", "float") and self.mc.device not in MC_WORD_DEVICES:
                raise ValueError(
                    f"tag '{self.name}': {self.data_type} data_type requires word device "
                    f"({sorted(MC_WORD_DEVICES)}), got {self.mc.device}"
                )

    # 외부 쓰기 허용 여부
    @property
    def writable(self) -> bool:
        return self.role in ROLE_WRITABLE


@dataclass
class SimConfig:
    equipment_name: str
    protocol: str
    host: str
    port: int
    sampling_ms: int = 1000
    namespace: Optional[str] = None
    tags: List[TagConfig] = field(default_factory=list)

    def __post_init__(self) -> None:
        if self.protocol not in ("modbus", "opcua", "mcprotocol"):
            raise ValueError(f"invalid protocol: {self.protocol}")
        if self.sampling_ms <= 0:
            raise ValueError("sampling_ms must be positive")

        # power 태그가 정확히 1개 있어야 함
        powers = [t for t in self.tags if t.role == "power"]
        if len(powers) != 1:
            raise ValueError(f"exactly one 'power' role tag is required, got {len(powers)}")

        # sensor 의 source_sp 가 실제 setpoint 를 가리키는지
        names = {t.name: t for t in self.tags}
        for t in self.tags:
            if t.role == "sensor" and t.source_sp:
                sp = names.get(t.source_sp)
                if not sp or sp.role != "setpoint":
                    raise ValueError(
                        f"sensor '{t.name}' source_sp='{t.source_sp}' "
                        f"is not a valid setpoint tag"
                    )

        # mcprotocol 일 때는 모든 태그에 mc 매핑이 있어야 한다
        if self.protocol == "mcprotocol":
            for t in self.tags:
                if t.mc is None:
                    raise ValueError(
                        f"mcprotocol requires mc mapping for all tags, missing on '{t.name}'"
                    )
            # 주소 중복 검사 (하나의 워드에 두 개 올려서 충돌하면 절대 안됨)
            seen: dict[tuple[str, int], str] = {}
            for t in self.tags:
                key = (t.mc.device, t.mc.address)
                if key in seen:
                    raise ValueError(
                        f"mc address conflict: {t.name} and {seen[key]} "
                        f"both map to {t.mc.device}{t.mc.address}"
                    )
                seen[key] = t.name
                # float 는 2 word 점유… 다음 워드도 등록
                if t.data_type == "float":
                    key2 = (t.mc.device, t.mc.address + 1)
                    if key2 in seen:
                        raise ValueError(
                            f"mc address conflict (float occupies 2 words): "
                            f"{t.name}@{t.mc.device}{t.mc.address+1} vs {seen[key2]}"
                        )
                    seen[key2] = t.name + "(+1)"


_ENV_RE = re.compile(r"\$\{([A-Z0-9_]+)(?::-([^}]*))?\}")


def _expand_env(value: Any) -> Any:
    """재귀적으로 문자열 안의 ${VAR} / ${VAR:-default} 처리.

    예: "${LINE_ID}_CAST-01" + env LINE_ID=LINE-01 → "LINE-01_CAST-01"
    """
    if isinstance(value, str):
        def repl(m: re.Match) -> str:
            var, default = m.group(1), m.group(2)
            val = os.environ.get(var)
            if val is not None:
                return val
            if default is not None:
                return default
            raise KeyError(
                f"environment variable '{var}' is not set "
                f"(used in config: '{value}')"
            )
        return _ENV_RE.sub(repl, value)
    if isinstance(value, dict):
        return {k: _expand_env(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_expand_env(v) for v in value]
    return value


def load_config(path: str | Path) -> SimConfig:
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"config not found: {p}")
    with p.open("r", encoding="utf-8") as f:
        raw = json.load(f)
    raw = _expand_env(raw)
    tags = [TagConfig(**t) for t in raw.pop("tags", [])]
    return SimConfig(tags=tags, **raw)
