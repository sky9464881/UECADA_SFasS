from __future__ import annotations

import math
import random
import re
from pathlib import Path
from typing import NamedTuple

import numpy as np
from scipy.io import loadmat


class BearingFile(NamedTuple):
    path: Path
    sample_rate_hz: int
    rotating_speed_rpm: int
    fault_kind: str = "normal"


NORMAL_ROTATION_TEMPLATES: tuple[tuple[float, float, float, float], ...] = (
    # base_hz, second-harmonic sine, second-harmonic cosine, third-harmonic sine.
    # These templates were checked against the v2 spectrogram model across the
    # NORMAL RMS range so the simulator varies without leaving the normal class.
    (10.0, 0.0, 0.0, -0.1),
    (10.0, 0.1, 0.0, -0.1),
    (10.0, -0.1, 0.0, -0.1),
    (10.0, -0.1, 0.0, -0.05),
    (10.0, 0.2, 0.0, -0.05),
    (10.0, -0.3, 0.0, 0.0),
    (10.0, 0.1, 0.0, -0.05),
    (10.0, 0.15, 0.0, -0.1),
    (10.0, 0.15, 0.0, -0.05),
    (10.0, -0.2, 0.15, -0.05),
    (10.0, -0.3, 0.15, 0.1),
    (10.0, -0.2, 0.0, -0.1),
)


def _stable_index(text: str, modulo: int) -> int:
    return sum((index + 1) * ord(char) for index, char in enumerate(text)) % modulo


class BearingWindowSource:
    """Slices healthy bearing waveforms into fixed duration windows."""

    _sampling_re = re.compile(r"SamplingRate_(\d+)")
    _speed_re = re.compile(r"RotatingSpeed_(\d+)")
    _fault_kinds = {"bearing", "looseness", "misalignment", "unbalance"}

    def __init__(
        self,
        root: Path,
        sample_rate_hz: int = 16000,
        rotating_speed_rpm: int = 1200,
        window_seconds: float = 2.0,
        stride_samples: int | None = None,
    ) -> None:
        self.root = self._resolve_root(root)
        self.requested_sample_rate_hz = sample_rate_hz
        self.requested_rotating_speed_rpm = rotating_speed_rpm
        self.window_seconds = window_seconds
        self.requested_stride_samples = stride_samples
        self._files = self._discover_files()
        self._fault_files_by_kind = self._discover_fault_files()
        self.sample_rate_hz = self._files[0].sample_rate_hz if self._files else self.requested_sample_rate_hz
        self.sample_count = max(1, int(round(self.sample_rate_hz * self.window_seconds)))
        self.stride_samples = max(1, int(stride_samples or self.sample_rate_hz))
        self._data_cache: dict[Path, np.ndarray] = {}
        self._cursor_by_equipment: dict[str, int] = {}
        self._file_by_equipment: dict[str, BearingFile] = {}
        self._fault_file_by_equipment: dict[tuple[str, str], BearingFile] = {}

    @staticmethod
    def _resolve_root(root: Path) -> Path:
        if root.exists():
            return root

        candidates = [
            Path.cwd() / "data" / "raw_mat" / root.name,
            Path(__file__).resolve().parents[3] / "data" / "raw_mat" / root.name,
        ]
        for candidate in candidates:
            if candidate.exists():
                return candidate
        return root

    def _metadata_for_path(self, path: Path, fault_kind: str = "normal") -> BearingFile | None:
        sample_match = self._sampling_re.search(str(path.parent.parent))
        speed_match = self._speed_re.search(str(path.parent))
        if not sample_match or not speed_match:
            return None
        return BearingFile(
            path=path,
            sample_rate_hz=int(sample_match.group(1)),
            rotating_speed_rpm=int(speed_match.group(1)),
            fault_kind=fault_kind,
        )

    def _sort_best_files(self, files: list[BearingFile]) -> list[BearingFile]:
        if not files:
            return []

        files.sort(
            key=lambda item: (
                abs(item.sample_rate_hz - self.requested_sample_rate_hz),
                abs(item.rotating_speed_rpm - self.requested_rotating_speed_rpm),
                str(item.path),
            )
        )

        best_sample_rate = files[0].sample_rate_hz
        best_speed = min(
            (item.rotating_speed_rpm for item in files if item.sample_rate_hz == best_sample_rate),
            key=lambda speed: abs(speed - self.requested_rotating_speed_rpm),
        )
        return [
            item
            for item in files
            if item.sample_rate_hz == best_sample_rate and item.rotating_speed_rpm == best_speed
        ]

    def _discover_files(self) -> list[BearingFile]:
        if not self.root.exists():
            return []

        healthy_files: list[BearingFile] = []
        for path in self.root.rglob("H_H_*.mat"):
            item = self._metadata_for_path(path)
            if item is not None:
                healthy_files.append(item)

        return self._sort_best_files(healthy_files)

    def _discover_fault_files(self) -> dict[str, list[BearingFile]]:
        if not self.root.exists():
            return {kind: [] for kind in self._fault_kinds}

        by_kind: dict[str, list[BearingFile]] = {kind: [] for kind in self._fault_kinds}
        for path in self.root.rglob("*.mat"):
            kinds = self._fault_kinds_for_path(path)
            for kind in kinds:
                item = self._metadata_for_path(path, kind)
                if item is not None:
                    by_kind[kind].append(item)

        return {kind: self._sort_best_files(files) for kind, files in by_kind.items()}

    @staticmethod
    def _fault_kinds_for_path(path: Path) -> set[str]:
        parts = path.stem.upper().split("_")
        if len(parts) < 2:
            return set()

        condition = parts[0]
        fault = parts[1]
        kinds: set[str] = set()

        if fault in {"B", "IR", "OR"}:
            kinds.add("bearing")
        if condition == "L" and fault == "H":
            kinds.add("looseness")
        if condition in {"M1", "M2", "M3"} and fault == "H":
            kinds.add("misalignment")
        if condition in {"U1", "U2", "U3"} and fault == "H":
            kinds.add("unbalance")

        return kinds

    def _file_for(self, equipment_id: str) -> BearingFile:
        if not self._files:
            raise FileNotFoundError(f"No bearing files are available under {self.root}")
        if equipment_id not in self._file_by_equipment:
            self._file_by_equipment[equipment_id] = self._files[_stable_index(equipment_id, len(self._files))]
        return self._file_by_equipment[equipment_id]

    def _load_data(self, bearing_file: BearingFile) -> np.ndarray:
        if bearing_file.path not in self._data_cache:
            mat = loadmat(bearing_file.path, variable_names=["Data"])
            data = np.asarray(mat["Data"], dtype=np.float64).reshape(-1)
            data = data - float(np.mean(data))
            self._data_cache[bearing_file.path] = data
        return self._data_cache[bearing_file.path]

    def next_raw_window(self, equipment_id: str) -> np.ndarray:
        if not self._files:
            return self._synthetic_window(equipment_id)

        bearing_file = self._file_for(equipment_id)
        return self._next_window_from_file(equipment_id, bearing_file)

    def next_fault_window(self, equipment_id: str, fault_kind: str) -> np.ndarray:
        normalized_kind = fault_kind.lower()
        files = self._fault_files_by_kind.get(normalized_kind, [])
        if not files:
            return self.next_raw_window(equipment_id)

        cache_key = (equipment_id, normalized_kind)
        if cache_key not in self._fault_file_by_equipment:
            index = _stable_index(f"{equipment_id}:{normalized_kind}", len(files))
            self._fault_file_by_equipment[cache_key] = files[index]

        return self._next_window_from_file(
            f"{equipment_id}:{normalized_kind}",
            self._fault_file_by_equipment[cache_key],
        )

    def _next_window_from_file(self, cursor_key: str, bearing_file: BearingFile) -> np.ndarray:
        data = self._load_data(bearing_file)
        cursor = self._cursor_by_equipment.get(cursor_key, _stable_index(cursor_key, len(data)))
        end = cursor + self.sample_count
        if end <= len(data):
            window = data[cursor:end]
        else:
            first = data[cursor:]
            second = data[: end % len(data)]
            window = np.concatenate([first, second])
        self._cursor_by_equipment[cursor_key] = (cursor + self.stride_samples) % len(data)
        return np.array(window, copy=True)

    def _synthetic_window(self, equipment_id: str) -> np.ndarray:
        synthetic_period = max(self.sample_rate_hz * 600, self.sample_count)
        cursor = self._cursor_by_equipment.get(equipment_id, _stable_index(equipment_id, synthetic_period))
        indices = np.arange(cursor, cursor + self.sample_count, dtype=np.float64)
        time_s = indices / float(self.sample_rate_hz)
        shaft_hz = max(1.0, self.requested_rotating_speed_rpm / 60.0)
        phase = _stable_index(equipment_id, 360) * math.pi / 180.0

        values = (
            np.sin(2.0 * math.pi * shaft_hz * time_s + phase)
            + 0.35 * np.sin(2.0 * math.pi * shaft_hz * 3.1 * time_s + phase / 2.0)
            + 0.18 * np.sin(2.0 * math.pi * shaft_hz * 5.7 * time_s + phase / 3.0)
        )
        rng_seed = (_stable_index(equipment_id, 2**31 - 1) + cursor) % (2**32)
        noise = np.random.default_rng(rng_seed).normal(0.0, 0.08, self.sample_count)
        window = values + noise
        window = window - float(np.mean(window))
        self._cursor_by_equipment[equipment_id] = (cursor + self.stride_samples) % synthetic_period
        return np.asarray(window, dtype=np.float64)


class VibrationGenerator:
    def __init__(self, source: BearingWindowSource, seed: int | None = None) -> None:
        self.source = source
        self.rng = random.Random(seed)
        self._normal_template_cursor_by_equipment: dict[str, int] = {}

    def next_window(
        self,
        equipment_id: str,
        operating_state: str,
        health_state: str,
        target_rms_mm_s: float,
        fault_kind: str | None = None,
    ) -> dict[str, object]:
        if operating_state == "OFF":
            vibration = self._idle_window(target_rms_mm_s)
        elif health_state == "NORMAL":
            vibration = self._normal_rotation_window(equipment_id, target_rms_mm_s)
        elif fault_kind:
            raw = self.source.next_fault_window(equipment_id, fault_kind)
            vibration = self._scaled_bearing_window(raw, target_rms_mm_s, health_state)
        else:
            raw = self.source.next_raw_window(equipment_id)
            vibration = self._scaled_bearing_window(raw, target_rms_mm_s, health_state)

        rms_value = rms(vibration)
        return {
            "vibration_raw": vibration,
            "vibration_rms": rms_value,
            "sample_rate_hz": self.source.sample_rate_hz,
            "sample_count": self.source.sample_count,
            "stride": self.source.stride_samples,
        }

    def next_axes(
        self,
        equipment_id: str,
        operating_state: str,
        health_state: str,
        target_rms_mm_s: float,
        fault_kind: str | None = None,
    ) -> dict[str, object]:
        window = self.next_window(equipment_id, operating_state, health_state, target_rms_mm_s, fault_kind)
        raw = np.asarray(window["vibration_raw"])
        axes = self._normalize_axes(*self._derived_axes(raw), target_rms_mm_s)

        x, y, z = axes
        rms = float(np.sqrt(np.mean((x * x + y * y + z * z) / 3.0)))
        return {
            "vibration_raw": raw,
            "vibration_x": x,
            "vibration_y": y,
            "vibration_z": z,
            "vibration_rms": rms,
            "sample_rate_hz": self.source.sample_rate_hz,
            "sample_count": self.source.sample_count,
            "stride": self.source.stride_samples,
        }

    def _idle_window(self, target_rms_mm_s: float) -> np.ndarray:
        sigma = max(target_rms_mm_s, 0.005)
        values = np.random.default_rng(self.rng.randrange(1, 2**32)).normal(
            0.0, sigma, self.source.sample_count
        )
        return self._normalize_window(values, target_rms_mm_s)

    def _normal_rotation_window(self, equipment_id: str, target_rms_mm_s: float) -> np.ndarray:
        cursor = self._normal_template_cursor_by_equipment.get(equipment_id, 0)
        template_index = (_stable_index(equipment_id, len(NORMAL_ROTATION_TEMPLATES)) + cursor) % len(
            NORMAL_ROTATION_TEMPLATES
        )
        self._normal_template_cursor_by_equipment[equipment_id] = cursor + 1
        shaft_hz, second_sin, second_cos, third_sin = NORMAL_ROTATION_TEMPLATES[template_index]
        indices = np.arange(self.source.sample_count, dtype=np.float64)
        time_s = indices / float(self.source.sample_rate_hz)
        values = (
            np.sin(2.0 * math.pi * shaft_hz * time_s)
            + second_sin * np.sin(2.0 * math.pi * shaft_hz * 2.0 * time_s)
            + second_cos * np.cos(2.0 * math.pi * shaft_hz * 2.0 * time_s)
            + third_sin * np.sin(2.0 * math.pi * shaft_hz * 3.0 * time_s + 0.31)
        )
        return self._normalize_window(values, target_rms_mm_s)

    def _idle_noise(self, target_rms_mm_s: float) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        sigma = max(target_rms_mm_s, 0.005)
        values = np.random.default_rng(self.rng.randrange(1, 2**32)).normal(
            0.0, sigma, (3, self.source.sample_count)
        )
        return self._normalize_axes(values[0], values[1], values[2], target_rms_mm_s)

    def _scaled_bearing_window(
        self,
        raw: np.ndarray,
        target_rms_mm_s: float,
        health_state: str,
    ) -> np.ndarray:
        raw_rms = rms(raw)
        if raw_rms <= 1e-12:
            raw = raw + 1e-6

        vibration = raw / max(raw_rms, 1e-12)
        if health_state in {"WARNING", "DANGER"}:
            vibration = self._add_window_impacts(vibration, health_state)

        return self._normalize_window(vibration, target_rms_mm_s)

    def _scaled_bearing_axes(
        self,
        raw: np.ndarray,
        target_rms_mm_s: float,
        health_state: str,
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        raw_rms = float(np.sqrt(np.mean(raw * raw)))
        if raw_rms <= 1e-12:
            raw = raw + 1e-6
            raw_rms = float(np.sqrt(np.mean(raw * raw)))

        x = raw / raw_rms
        y = np.roll(raw, max(1, len(raw) // 11)) / raw_rms * 0.82
        z = np.roll(raw, max(1, len(raw) // 7)) / raw_rms * 0.67

        if health_state in {"WARNING", "DANGER"}:
            x, y, z = self._add_impacts(x, y, z, health_state)

        return self._normalize_axes(x, y, z, target_rms_mm_s)

    def _add_window_impacts(self, values: np.ndarray, health_state: str) -> np.ndarray:
        count = 6 if health_state == "WARNING" else 14
        width = 16 if health_state == "WARNING" else 28
        amplitude = 1.5 if health_state == "WARNING" else 3.0
        impulse = np.zeros_like(values)
        for _ in range(count):
            center = self.rng.randrange(0, len(values))
            start = max(0, center - width // 2)
            end = min(len(values), start + width)
            shape = np.hanning(max(2, end - start)) * amplitude * self.rng.choice([-1.0, 1.0])
            impulse[start:end] += shape
        return values + impulse

    def _add_impacts(
        self,
        x: np.ndarray,
        y: np.ndarray,
        z: np.ndarray,
        health_state: str,
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        count = 6 if health_state == "WARNING" else 14
        width = 16 if health_state == "WARNING" else 28
        amplitude = 1.5 if health_state == "WARNING" else 3.0
        impulse = np.zeros_like(x)
        for _ in range(count):
            center = self.rng.randrange(0, len(x))
            start = max(0, center - width // 2)
            end = min(len(x), start + width)
            shape = np.hanning(max(2, end - start)) * amplitude * self.rng.choice([-1.0, 1.0])
            impulse[start:end] += shape
        return x + impulse, y + np.roll(impulse, width), z + np.roll(impulse, width * 2)

    @staticmethod
    def _derived_axes(raw: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        y = np.roll(raw, max(1, len(raw) // 11)) * 0.82
        z = np.roll(raw, max(1, len(raw) // 7)) * 0.67
        return raw, y, z

    @staticmethod
    def _normalize_window(values: np.ndarray, target_rms_mm_s: float) -> np.ndarray:
        current = rms(values)
        scale = target_rms_mm_s / current if current > 1e-12 else 0.0
        return values * scale

    @staticmethod
    def _normalize_axes(
        x: np.ndarray,
        y: np.ndarray,
        z: np.ndarray,
        target_rms_mm_s: float,
    ) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        current = float(np.sqrt(np.mean((x * x + y * y + z * z) / 3.0)))
        scale = target_rms_mm_s / current if current > 1e-12 else 0.0
        return x * scale, y * scale, z * scale


def rms(values: np.ndarray) -> float:
    return math.sqrt(float(np.mean(values * values)))
