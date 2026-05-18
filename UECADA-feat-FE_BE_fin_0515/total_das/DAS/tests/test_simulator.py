from __future__ import annotations

import unittest
from pathlib import Path

from das_simulator.simulator import SensorWindowSimulator
from das_simulator.specs import COMMON_RANGES, EQUIPMENT, equipment_by_id
from das_simulator.vibration import BearingWindowSource, VibrationGenerator


class SensorWindowSimulatorTests(unittest.TestCase):
    def _simulator(self, *, include_waveform: bool = False, health_state: str = "NORMAL") -> SensorWindowSimulator:
        source = BearingWindowSource(
            Path("BearingType_DeepGrooveBall"),
            sample_rate_hz=16000,
            rotating_speed_rpm=1200,
            window_seconds=2.0,
            stride_samples=16000,
        )
        return SensorWindowSimulator(
            VibrationGenerator(source, seed=123),
            seed=123,
            include_waveform=include_waveform,
            health_state=health_state,
        )

    def test_compact_payload_shape(self) -> None:
        equipment = equipment_by_id({"CAST-01"})[0]
        payload = self._simulator(include_waveform=False).next_payload(equipment)

        self.assertEqual(payload["sample"]["period_sec"], 1.0)
        self.assertEqual(payload["equipment"]["equipment_id"], "CAST-01")
        self.assertEqual(payload["equipment"]["instance_id"], "LINE-01-CAST-01")
        self.assertEqual(payload["line"]["line_id"], "LINE-01")
        self.assertEqual(
            set(payload["tags"]),
            {"voltage_v", "current_a", "equipment_temperature_c", "vibration_rms_mm_s"},
        )
        self.assertEqual(
            [sensor["sensor_id"] for sensor in payload["sensors"]],
            ["VIB-01", "TEMP-01", "CUR-01", "VOLT-01"],
        )
        self.assertIn("window", payload["sensors"][0])
        self.assertNotIn("vibration_raw", payload["sensors"][0]["values"])
        self.assertEqual(payload["sensors"][0]["window"]["stride"], 16000)
        self.assertEqual(payload["sensors"][0]["window"]["model_input"]["input_key"], "values.vibration_raw")
        self.assertNotIn("sample_count", payload["sensors"][1]["sample"])

    def test_waveform_payload_has_model_raw_window(self) -> None:
        equipment = equipment_by_id({"CAST-01"})[0]
        payload = self._simulator(include_waveform=True).next_payload(equipment)
        vibration = payload["sensors"][0]["values"]

        self.assertEqual(payload["sensors"][0]["window"]["sample_rate_hz"], 16000)
        self.assertEqual(payload["sensors"][0]["window"]["sample_count"], 32000)
        self.assertEqual(payload["sensors"][0]["window"]["window_size"], 32000)
        self.assertEqual(payload["sensors"][0]["window"]["stride"], 16000)
        self.assertEqual(len(vibration["vibration_raw"]), 32000)
        low, high = COMMON_RANGES["CAST"]["vibration_rms_mm_s"]["NORMAL"]
        self.assertGreaterEqual(payload["tags"]["vibration_rms_mm_s"], low)
        self.assertLessEqual(payload["tags"]["vibration_rms_mm_s"], high)

    def test_danger_payload_uses_danger_health(self) -> None:
        equipment = equipment_by_id({"TEST-01"})[0]
        payload = self._simulator(include_waveform=False, health_state="DANGER").next_payload(equipment)

        self.assertEqual(payload["operation"]["health"], "DANGER")
        low, high = COMMON_RANGES["TEST"]["current_a"]["DANGER"]
        self.assertGreaterEqual(payload["tags"]["current_a"], low)
        self.assertLessEqual(payload["tags"]["current_a"], high)
        self.assertEqual(
            [sensor["sensor_id"] for sensor in payload["sensors"]],
            ["VIB-01", "TEMP-01", "CUR-01", "VOLT-01"],
        )

    def test_three_lines_are_built(self) -> None:
        self.assertEqual(len(EQUIPMENT), 27)
        self.assertEqual(len(equipment_by_id({"LINE-01"})), 9)
        self.assertEqual(len(equipment_by_id({"CAST-01"})), 3)
        self.assertEqual(equipment_by_id({"LINE-03-TEST-02"})[0].line_id, "LINE-03")


if __name__ == "__main__":
    unittest.main()
