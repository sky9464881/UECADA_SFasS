from __future__ import annotations

import json
import random
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import numpy as np

from .specs import (
    COMMON_RANGES,
    SENSOR_CATALOG,
    VOLTAGE_PROFILES,
    EquipmentInstance,
)
from .vibration import VibrationGenerator


STATE_KEYS = {"OFF", "STANDBY", "NORMAL", "WARNING", "DANGER"}
VIBRATION_MODEL_INPUT = {
    "model_version": "spectrogram-pca-rf-v2",
    "runtime_input": "raw vibration window only",
    "input_key": "values.vibration_raw",
    "preprocessing_version": "raw-stft-64x64-maxnorm-v2",
    "spectrogram_shape": [64, 64],
    "flattened_shape": [4096],
    "stft_params": {
        "window": "hann",
        "nperseg": 256,
        "noverlap": 128,
        "detrend": False,
        "scaling": "spectrum",
        "mode": "magnitude",
    },
    "log_transform": False,
    "per_window_max_normalization": True,
    "per_window_max_normalization_eps": 1e-8,
    "resize": {
        "method": "scipy.ndimage.zoom",
        "order": 1,
    },
    "scaler": "StandardScaler",
    "pca": {
        "n_components": 100,
        "random_state": 42,
    },
    "classifier": {
        "type": "RandomForestClassifier",
        "n_estimators": 300,
        "class_weight": "balanced",
        "random_state": 42,
    },
    "classes": ["normal", "bearing", "looseness", "misalignment", "unbalance"],
    "validation": "Leave-One-RPM-Out or grouped split by RPM/file",
}


@dataclass
class RuntimeState:
    seq: int = 0
    previous_values: dict[str, float] = field(default_factory=dict)
    auto_health_state: str = "NORMAL"
    auto_health_remaining: int = 0


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


class SensorWindowSimulator:
    def __init__(
        self,
        vibration: VibrationGenerator,
        operating_state: str = "RUN",
        health_state: str = "NORMAL",
        seed: int | None = None,
        warning_rate: float = 0.02,
        danger_rate: float = 0.004,
        precision: int = 4,
        include_waveform: bool = True,
    ) -> None:
        self.vibration = vibration
        self.operating_state = operating_state.upper()
        self.health_state = health_state.upper()
        self.warning_rate = warning_rate
        self.danger_rate = danger_rate
        self.precision = precision
        self.include_waveform = include_waveform
        self.rng = random.Random(seed)
        self._state: dict[str, RuntimeState] = {}

    def next_payload(self, equipment: EquipmentInstance) -> dict[str, Any]:
        state = self._state.setdefault(equipment.instance_id, RuntimeState())
        op_state = self._resolve_operating_state(state)
        health_state = self._resolve_health_state(state, op_state)
        status_key = self._status_key(op_state, health_state)

        common = self._generate_common_values(equipment, state, op_state, status_key)
        cycle_elapsed = state.seq % equipment.cycle_time_sec
        timestamp = utc_now_iso()

        vibration_window = self.vibration.next_window(
            equipment_id=equipment.instance_id,
            operating_state=op_state,
            health_state=health_state,
            target_rms_mm_s=common["vibration_rms_mm_s"],
        )

        tags = {
            "voltage_v": common["voltage_v"],
            "current_a": common["current_a"],
            "equipment_temperature_c": common["equipment_temperature_c"],
            "vibration_rms_mm_s": self._round(vibration_window["vibration_rms"]),
        }
        if "control_voltage_v" in common:
            tags["control_voltage_v"] = common["control_voltage_v"]

        sensors = self._build_sensor_payloads(common, vibration_window, timestamp, state.seq)
        payload = {
            "schema_version": "1.0",
            "timestamp": timestamp,
            "line": {
                "line_id": equipment.line_id,
                "line_index": equipment.line_index,
                "line_count": equipment.line_count,
            },
            "equipment": {
                "instance_id": equipment.instance_id,
                "equipment_id": equipment.equipment_id,
                "equipment_type": equipment.equipment_type,
                "display_name": equipment.display_name,
            },
            "plc": {
                "device_name": equipment.plc_device,
            },
            "operation": {
                "state": op_state,
                "health": health_state,
                "cycle_time_sec": equipment.cycle_time_sec,
                "cycle_elapsed_sec": cycle_elapsed,
                "cycle_index": state.seq // equipment.cycle_time_sec,
            },
            "sample": {
                "seq": state.seq,
                "period_sec": 1.0,
                "timestamp": timestamp,
            },
            "tags": tags,
            "sensors": sensors,
        }
        state.seq += 1
        return payload

    def _resolve_operating_state(self, state: RuntimeState) -> str:
        if self.operating_state != "AUTO":
            return self.operating_state
        # A compact production rhythm: RUN most of the time with a brief standby.
        return "STANDBY" if state.seq % 180 in range(0, 10) else "RUN"

    def _resolve_health_state(self, state: RuntimeState, operating_state: str) -> str:
        if operating_state == "OFF":
            return "NORMAL"
        if self.health_state != "AUTO":
            return self.health_state
        if state.auto_health_remaining > 0:
            state.auto_health_remaining -= 1
            return state.auto_health_state

        draw = self.rng.random()
        if draw < self.danger_rate:
            state.auto_health_state = "DANGER"
            state.auto_health_remaining = self.rng.randint(3, 8)
        elif draw < self.danger_rate + self.warning_rate:
            state.auto_health_state = "WARNING"
            state.auto_health_remaining = self.rng.randint(5, 15)
        else:
            state.auto_health_state = "NORMAL"
            state.auto_health_remaining = 0
        return state.auto_health_state

    @staticmethod
    def _status_key(operating_state: str, health_state: str) -> str:
        if operating_state == "OFF":
            return "OFF"
        if operating_state == "STANDBY":
            return "STANDBY"
        return health_state if health_state in {"WARNING", "DANGER"} else "NORMAL"

    def _generate_common_values(
        self,
        equipment: EquipmentInstance,
        state: RuntimeState,
        operating_state: str,
        status_key: str,
    ) -> dict[str, float]:
        ranges = COMMON_RANGES[equipment.equipment_type]
        current = self._range_value(state, "current_a", ranges["current_a"][status_key])
        equipment_temp = self._range_value(
            state,
            "equipment_temperature_c",
            ranges["equipment_temperature_c"][status_key],
        )
        vibration_rms = self._range_value(
            state,
            "vibration_rms_mm_s",
            ranges["vibration_rms_mm_s"][status_key],
        )
        voltage_values = self._voltage_values(equipment.equipment_type, state, operating_state, status_key)

        values = {
            "current_a": self._round(current),
            "equipment_temperature_c": self._round(equipment_temp),
            "vibration_rms_mm_s": self._round(vibration_rms),
            **voltage_values,
        }
        return values

    def _voltage_values(
        self,
        equipment_type: str,
        state: RuntimeState,
        operating_state: str,
        status_key: str,
    ) -> dict[str, float]:
        profile = VOLTAGE_PROFILES[equipment_type]
        nominal = float(profile["nominal_v"])
        if operating_state == "OFF" or status_key == "DANGER":
            voltage_range = (0.0, 1.0) if operating_state == "OFF" else (0.0, nominal * 0.08)
        elif status_key == "STANDBY":
            voltage_range = (nominal * 0.99, nominal * 1.01)
        elif status_key == "WARNING":
            voltage_range = self.rng.choice(
                [(nominal * 0.78, nominal * 0.90), (nominal * 1.10, nominal * 1.16)]
            )
        else:
            voltage_range = (nominal * 0.90, nominal * 1.10)

        values = {"voltage_v": self._round(self._range_value(state, "voltage_v", voltage_range))}
        control_nominal = profile.get("control_voltage_v")
        if control_nominal is not None:
            if operating_state == "OFF" or status_key == "DANGER":
                control_range = (0.0, 1.0)
            elif status_key == "WARNING":
                control_range = (control_nominal * 0.85, control_nominal * 0.94)
            else:
                control_range = (control_nominal * 0.97, control_nominal * 1.03)
            values["control_voltage_v"] = self._round(
                self._range_value(state, "control_voltage_v", control_range)
            )
        return values

    def _range_value(self, state: RuntimeState, tag: str, value_range: tuple[float, float]) -> float:
        low, high = value_range
        if low == high:
            value = low
        else:
            target = self.rng.uniform(low, high)
            previous = state.previous_values.get(tag)
            if previous is None or not (low <= previous <= high):
                value = target
            else:
                value = previous * 0.82 + target * 0.18
        state.previous_values[tag] = value
        return value

    def _build_sensor_payloads(
        self,
        common: dict[str, float],
        vibration_window: dict[str, Any],
        timestamp: str,
        seq: int,
    ) -> list[dict[str, Any]]:
        scalar_sample = {
            "seq": seq,
            "period_sec": 1.0,
            "timestamp": timestamp,
        }
        sensors = [
            {
                **SENSOR_CATALOG["TEMP-01"],
                "sensor_id": "TEMP-01",
                "sample": scalar_sample,
                "values": {"equipment_temperature": common["equipment_temperature_c"]},
                "unit": "degC",
            },
            {
                **SENSOR_CATALOG["CUR-01"],
                "sensor_id": "CUR-01",
                "sample": scalar_sample,
                "values": {"current": common["current_a"]},
                "unit": "A",
            },
            {
                **SENSOR_CATALOG["VOLT-01"],
                "sensor_id": "VOLT-01",
                "sample": scalar_sample,
                "values": {
                    "voltage": common["voltage_v"],
                },
                "unit": {"voltage": "V"},
            },
        ]
        if "control_voltage_v" in common:
            sensors[2]["values"]["control_voltage"] = common["control_voltage_v"]
            sensors[2]["unit"]["control_voltage"] = "VDC"

        vib_values: dict[str, Any] = {
            "vibration_rms": self._round(vibration_window["vibration_rms"]),
        }
        if self.include_waveform:
            vib_values.update(
                {
                    "vibration_raw": self._array(vibration_window["vibration_raw"]),
                }
            )
        sensors.insert(
            0,
            {
                **SENSOR_CATALOG["VIB-01"],
                "sensor_id": "VIB-01",
                "window": {
                    "seq": seq,
                    "period_sec": self.vibration.source.window_seconds,
                    "window_seconds": self.vibration.source.window_seconds,
                    "sample_rate_hz": vibration_window["sample_rate_hz"],
                    "sample_count": vibration_window["sample_count"],
                    "window_size": vibration_window["sample_count"],
                    "stride": vibration_window["stride"],
                    "started_at": timestamp,
                    "model_input": {
                        **VIBRATION_MODEL_INPUT,
                        "sampling_rate": vibration_window["sample_rate_hz"],
                        "window_seconds": self.vibration.source.window_seconds,
                        "window_size": vibration_window["sample_count"],
                        "stride": vibration_window["stride"],
                    },
                },
                "values": vib_values,
                "unit": "mm/s",
            },
        )
        return sensors

    def _array(self, values: np.ndarray) -> list[float]:
        return np.round(values.astype(float), self.precision).tolist()

    def _round(self, value: float) -> float:
        return round(float(value), self.precision)


def payload_to_json(payload: dict[str, Any], pretty: bool = False) -> str:
    if pretty:
        return json.dumps(payload, ensure_ascii=False, indent=2)
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
