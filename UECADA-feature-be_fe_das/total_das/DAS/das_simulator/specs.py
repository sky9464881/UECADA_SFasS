from __future__ import annotations

from dataclasses import dataclass
from typing import Any


NumberRange = tuple[float, float]


@dataclass(frozen=True)
class EquipmentInstance:
    line_id: str
    line_index: int
    equipment_id: str
    equipment_type: str
    display_name: str
    plc_device: str
    cycle_time_sec: int
    line_count: int = 3

    @property
    def instance_id(self) -> str:
        return f"{self.line_id}-{self.equipment_id}"


SENSOR_CATALOG: dict[str, dict[str, str]] = {
    "VIB-01": {
        "name": "raw vibration sensor",
        "model": "ADXL356",
        "connection": "Analog/ADC",
    },
    "TEMP-01": {
        "name": "equipment surface temperature sensor",
        "model": "PT100 + MAX31865",
        "connection": "SPI",
    },
    "CUR-01": {
        "name": "clamp current sensor",
        "model": "SCT-013",
        "connection": "ADC",
    },
    "VOLT-01": {
        "name": "voltage/power sensor",
        "model": "PZEM-004T",
        "connection": "UART",
    },
    "ENV-01": {
        "name": "temperature/humidity sensor",
        "model": "SHT31/SHT35",
        "connection": "I2C",
    },
    "LIGHT-01": {
        "name": "illumination sensor",
        "model": "industrial illuminance sensor",
        "connection": "ADC",
    },
}


LINE_IDS = ("LINE-01", "LINE-02", "LINE-03")

EQUIPMENT_LAYOUT: tuple[tuple[str, str, str, str, int], ...] = (
    ("CAST-01", "CAST", "casting machine", "MELSEC iQ-R PLC", 60),
    ("CNC-01", "CNC", "machining center", "CNC M80V", 20),
    ("CNC-02", "CNC", "machining center", "CNC M80V", 20),
    ("CNC-03", "CNC", "machining center", "CNC M80V", 20),
    ("WASH-01", "WASH", "washing machine", "MELSEC iQ-F FX5U PLC", 60),
    ("ASSY-01", "ASSY", "assembly machine", "MELSEC iQ-F FX5U PLC", 30),
    ("ASSY-02", "ASSY", "assembly machine", "MELSEC iQ-F FX5U PLC", 30),
    ("TEST-01", "TEST", "inspection machine", "MELSEC iQ-F FX5U PLC", 30),
    ("TEST-02", "TEST", "inspection machine", "MELSEC iQ-F FX5U PLC", 30),
)


def _build_equipment() -> list[EquipmentInstance]:
    equipment: list[EquipmentInstance] = []
    for line_index, line_id in enumerate(LINE_IDS, start=1):
        for equipment_id, equipment_type, display_name, plc_device, cycle_time_sec in EQUIPMENT_LAYOUT:
            equipment.append(
                EquipmentInstance(
                    line_id=line_id,
                    line_index=line_index,
                    equipment_id=equipment_id,
                    equipment_type=equipment_type,
                    display_name=display_name,
                    plc_device=plc_device,
                    cycle_time_sec=cycle_time_sec,
                    line_count=len(LINE_IDS),
                )
            )
    return equipment


EQUIPMENT: list[EquipmentInstance] = _build_equipment()


COMMON_RANGES: dict[str, dict[str, dict[str, NumberRange]]] = {
    "CAST": {
        "current_a": {
            "OFF": (0.0, 0.5),
            "STANDBY": (2.0, 10.0),
            "NORMAL": (30.0, 60.0),
            "WARNING": (60.0, 75.0),
            "DANGER": (75.0, 95.0),
        },
        "equipment_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (35.0, 60.0),
            "NORMAL": (50.0, 80.0),
            "WARNING": (80.0, 90.0),
            "DANGER": (90.0, 110.0),
        },
        "ambient_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (20.0, 35.0),
            "NORMAL": (25.0, 40.0),
            "WARNING": (40.0, 45.0),
            "DANGER": (45.0, 55.0),
        },
        "humidity_rh": {
            "OFF": (30.0, 70.0),
            "STANDBY": (30.0, 70.0),
            "NORMAL": (30.0, 70.0),
            "WARNING": (70.0, 80.0),
            "DANGER": (80.0, 95.0),
        },
        "vibration_rms_mm_s": {
            "OFF": (0.0, 0.1),
            "STANDBY": (0.2, 0.8),
            "NORMAL": (0.8, 2.8),
            "WARNING": (2.8, 4.5),
            "DANGER": (4.5, 7.0),
        },
    },
    "CNC": {
        "current_a": {
            "OFF": (0.0, 0.5),
            "STANDBY": (2.0, 8.0),
            "NORMAL": (10.0, 35.0),
            "WARNING": (35.0, 45.0),
            "DANGER": (45.0, 65.0),
        },
        "equipment_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (25.0, 35.0),
            "NORMAL": (35.0, 55.0),
            "WARNING": (60.0, 70.0),
            "DANGER": (70.0, 90.0),
        },
        "ambient_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (20.0, 35.0),
            "NORMAL": (20.0, 35.0),
            "WARNING": (35.0, 40.0),
            "DANGER": (40.0, 50.0),
        },
        "humidity_rh": {
            "OFF": (30.0, 70.0),
            "STANDBY": (30.0, 70.0),
            "NORMAL": (30.0, 70.0),
            "WARNING": (70.0, 80.0),
            "DANGER": (80.0, 95.0),
        },
        "vibration_rms_mm_s": {
            "OFF": (0.0, 0.1),
            "STANDBY": (0.2, 0.8),
            "NORMAL": (0.8, 2.8),
            "WARNING": (2.8, 4.5),
            "DANGER": (4.5, 7.0),
        },
    },
    "WASH": {
        "current_a": {
            "OFF": (0.0, 0.5),
            "STANDBY": (3.0, 10.0),
            "NORMAL": (10.0, 40.0),
            "WARNING": (40.0, 50.0),
            "DANGER": (50.0, 70.0),
        },
        "equipment_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (35.0, 55.0),
            "NORMAL": (45.0, 70.0),
            "WARNING": (70.0, 80.0),
            "DANGER": (80.0, 95.0),
        },
        "ambient_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (20.0, 35.0),
            "NORMAL": (25.0, 38.0),
            "WARNING": (38.0, 42.0),
            "DANGER": (42.0, 52.0),
        },
        "humidity_rh": {
            "OFF": (30.0, 70.0),
            "STANDBY": (40.0, 75.0),
            "NORMAL": (50.0, 80.0),
            "WARNING": (80.0, 90.0),
            "DANGER": (90.0, 98.0),
        },
        "vibration_rms_mm_s": {
            "OFF": (0.0, 0.1),
            "STANDBY": (0.2, 0.8),
            "NORMAL": (0.8, 2.8),
            "WARNING": (2.8, 4.5),
            "DANGER": (4.5, 7.0),
        },
    },
    "ASSY": {
        "current_a": {
            "OFF": (0.0, 0.5),
            "STANDBY": (1.0, 3.0),
            "NORMAL": (3.0, 10.0),
            "WARNING": (10.0, 15.0),
            "DANGER": (15.0, 25.0),
        },
        "equipment_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (20.0, 35.0),
            "NORMAL": (25.0, 45.0),
            "WARNING": (50.0, 60.0),
            "DANGER": (60.0, 80.0),
        },
        "ambient_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (20.0, 35.0),
            "NORMAL": (20.0, 35.0),
            "WARNING": (35.0, 40.0),
            "DANGER": (40.0, 50.0),
        },
        "humidity_rh": {
            "OFF": (30.0, 70.0),
            "STANDBY": (30.0, 70.0),
            "NORMAL": (30.0, 70.0),
            "WARNING": (70.0, 80.0),
            "DANGER": (80.0, 95.0),
        },
        "vibration_rms_mm_s": {
            "OFF": (0.0, 0.1),
            "STANDBY": (0.2, 0.8),
            "NORMAL": (0.5, 2.0),
            "WARNING": (2.8, 4.5),
            "DANGER": (4.5, 7.0),
        },
    },
    "TEST": {
        "current_a": {
            "OFF": (0.0, 0.5),
            "STANDBY": (1.0, 3.0),
            "NORMAL": (2.0, 8.0),
            "WARNING": (8.0, 12.0),
            "DANGER": (12.0, 20.0),
        },
        "equipment_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (25.0, 35.0),
            "NORMAL": (30.0, 45.0),
            "WARNING": (50.0, 60.0),
            "DANGER": (60.0, 80.0),
        },
        "ambient_temperature_c": {
            "OFF": (20.0, 35.0),
            "STANDBY": (20.0, 35.0),
            "NORMAL": (20.0, 35.0),
            "WARNING": (35.0, 40.0),
            "DANGER": (40.0, 50.0),
        },
        "humidity_rh": {
            "OFF": (30.0, 70.0),
            "STANDBY": (30.0, 70.0),
            "NORMAL": (30.0, 70.0),
            "WARNING": (70.0, 80.0),
            "DANGER": (80.0, 95.0),
        },
        "vibration_rms_mm_s": {
            "OFF": (0.0, 0.1),
            "STANDBY": (0.1, 0.5),
            "NORMAL": (0.2, 1.0),
            "WARNING": (1.5, 2.8),
            "DANGER": (2.8, 5.0),
        },
        "illuminance_lx": {
            "OFF": (0.0, 0.0),
            "STANDBY": (900.0, 1100.0),
            "NORMAL": (900.0, 1300.0),
            "WARNING": (500.0, 850.0),
            "DANGER": (0.0, 400.0),
        },
    },
}


VOLTAGE_PROFILES: dict[str, dict[str, Any]] = {
    "CAST": {"nominal_v": 380.0, "phase": "3P", "control_voltage_v": None},
    "CNC": {"nominal_v": 380.0, "phase": "3P", "control_voltage_v": None},
    "WASH": {"nominal_v": 380.0, "phase": "3P", "control_voltage_v": None},
    "ASSY": {"nominal_v": 380.0, "phase": "3P", "control_voltage_v": 24.0},
    "TEST": {"nominal_v": 220.0, "phase": "1P", "control_voltage_v": 24.0},
}


PROCESS_RANGES: dict[str, dict[str, dict[str, NumberRange]]] = {
    "CAST": {
        "injection_pressure_mpa": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 5.0),
            "NORMAL": (30.0, 120.0),
            "WARNING": (121.0, 150.0),
            "DANGER": (151.0, 190.0),
        },
        "mold_temperature_c": {
            "OFF": (20.0, 60.0),
            "STANDBY": (150.0, 220.0),
            "NORMAL": (190.0, 240.0),
            "WARNING": (260.0, 280.0),
            "DANGER": (280.0, 320.0),
        },
        "cooling_water_flow_lpm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (5.0, 20.0),
            "NORMAL": (20.0, 60.0),
            "WARNING": (12.0, 18.0),
            "DANGER": (0.0, 12.0),
        },
    },
    "CNC": {
        "spindle_speed_rpm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 0.0),
            "NORMAL": (3000.0, 8000.0),
            "WARNING": (8001.0, 8800.0),
            "DANGER": (0.0, 0.0),
        },
        "tool_usage_percent": {
            "OFF": (0.0, 80.0),
            "STANDBY": (0.0, 80.0),
            "NORMAL": (0.0, 80.0),
            "WARNING": (80.0, 100.0),
            "DANGER": (100.0, 120.0),
        },
        "coolant_flow_lpm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 5.0),
            "NORMAL": (10.0, 30.0),
            "WARNING": (6.0, 8.0),
            "DANGER": (0.0, 6.0),
        },
    },
    "WASH": {
        "cleaning_concentration_percent": {
            "OFF": (2.0, 5.0),
            "STANDBY": (1.0, 5.0),
            "NORMAL": (2.0, 5.0),
            "WARNING": (7.0, 10.0),
            "DANGER": (10.0, 13.0),
        },
        "cleaning_temperature_c": {
            "OFF": (20.0, 45.0),
            "STANDBY": (40.0, 60.0),
            "NORMAL": (50.0, 75.0),
            "WARNING": (80.0, 85.0),
            "DANGER": (85.0, 100.0),
        },
        "cleaning_pressure_bar": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 1.0),
            "NORMAL": (2.0, 6.0),
            "WARNING": (1.2, 1.6),
            "DANGER": (0.0, 1.2),
        },
        "cleaning_water_flow_lpm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 10.0),
            "NORMAL": (20.0, 80.0),
            "WARNING": (12.0, 16.0),
            "DANGER": (0.0, 12.0),
        },
    },
    "ASSY": {
        "fastening_torque_nm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 0.0),
            "NORMAL": (30.0, 50.0),
            "WARNING": (50.1, 60.0),
            "DANGER": (60.1, 75.0),
        },
        "fastening_angle_deg": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 0.0),
            "NORMAL": (85.0, 95.0),
            "WARNING": (75.0, 84.0),
            "DANGER": (0.0, 70.0),
        },
        "press_force_n": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 0.0),
            "NORMAL": (500.0, 3000.0),
            "WARNING": (3001.0, 3900.0),
            "DANGER": (3901.0, 5000.0),
        },
    },
    "TEST": {
        "piston_bore_mm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (39.995, 40.005),
            "NORMAL": (39.98, 40.02),
            "WARNING": (40.014, 40.018),
            "DANGER": (40.021, 40.035),
        },
        "mounting_hole_mm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (10.19, 10.21),
            "NORMAL": (10.15, 10.25),
            "WARNING": (10.235, 10.245),
            "DANGER": (10.251, 10.3),
        },
        "leakage_sccm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (0.0, 0.5),
            "NORMAL": (0.0, 5.0),
            "WARNING": (5.0, 8.0),
            "DANGER": (8.0, 15.0),
        },
        "inspection_flow_lpm": {
            "OFF": (0.0, 0.0),
            "STANDBY": (4.0, 8.0),
            "NORMAL": (5.0, 12.0),
            "WARNING": (3.0, 5.0),
            "DANGER": (0.0, 3.0),
        },
        "inspection_cycle_time_sec": {
            "OFF": (0.0, 0.0),
            "STANDBY": (30.0, 60.0),
            "NORMAL": (30.0, 60.0),
            "WARNING": (60.0, 75.0),
            "DANGER": (75.0, 120.0),
        },
    },
}


def equipment_by_id(ids: set[str] | None = None) -> list[EquipmentInstance]:
    if not ids:
        return EQUIPMENT
    wanted = {item.upper() for item in ids}
    return [
        item
        for item in EQUIPMENT
        if item.instance_id.upper() in wanted
        or item.equipment_id.upper() in wanted
        or item.line_id.upper() in wanted
    ]
