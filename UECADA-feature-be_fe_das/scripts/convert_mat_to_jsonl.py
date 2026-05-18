from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import numpy as np
from scipy.io import loadmat


COMMON_SIGNAL_KEYS = ("Data", "data", "signal", "Signal", "vibration", "Vibration", "X")
FILENAME_PATTERN = re.compile(
    r"^(?P<rotating>[A-Z]\d?)_(?P<bearing>H|B|IR|OR)_(?P<rate>8|16)_(?P<model>\d+)_(?P<rpm>\d+)\.mat$",
    re.IGNORECASE,
)


def parse_start_time(value: str | None) -> datetime:
    if not value:
        return datetime.now(timezone.utc).replace(microsecond=0)

    normalized = value.replace("Z", "+00:00")
    parsed = datetime.fromisoformat(normalized)
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def iso_z(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def parse_metadata(mat_file: Path) -> dict[str, Any]:
    match = FILENAME_PATTERN.match(mat_file.name)
    if not match:
        return {}

    rate_token = match.group("rate")
    return {
        "samplingRate": 8000 if rate_token == "8" else 16000,
        "rpm": int(match.group("rpm")),
        "bearingModel": match.group("model"),
        "rotatingCondition": match.group("rotating").upper(),
        "bearingCondition": match.group("bearing").upper(),
    }


def load_signal(mat_file: Path, signal_key: str | None) -> np.ndarray:
    mat = loadmat(mat_file)

    if signal_key:
        if signal_key not in mat:
            raise KeyError(f"{signal_key!r} not found. Available keys: {available_keys(mat)}")
        signal = mat[signal_key]
    else:
        for key in COMMON_SIGNAL_KEYS:
            if key in mat:
                signal = mat[key]
                break
        else:
            numeric_items = [
                (key, value)
                for key, value in mat.items()
                if not key.startswith("__") and isinstance(value, np.ndarray) and np.issubdtype(value.dtype, np.number)
            ]
            if not numeric_items:
                raise KeyError(f"No numeric signal array found. Available keys: {available_keys(mat)}")
            signal = max(numeric_items, key=lambda item: item[1].size)[1]

    return np.asarray(signal, dtype=float).reshape(-1)


def available_keys(mat: dict[str, Any]) -> list[str]:
    return [key for key in mat.keys() if not key.startswith("__")]


def write_jsonl(
    *,
    mat_file: Path,
    output: Path,
    equipment_id: str,
    signal_key: str | None,
    sampling_rate: int | None,
    rpm: int | None,
    window_size: int,
    stride: int,
    max_windows: int | None,
    start_time: datetime,
    decimals: int,
    include_label: bool,
) -> int:
    metadata = parse_metadata(mat_file)
    resolved_sampling_rate = sampling_rate or metadata.get("samplingRate")
    resolved_rpm = rpm or metadata.get("rpm")

    if not resolved_sampling_rate:
        raise ValueError("sampling rate is unknown. Pass --sampling-rate or use a standard dataset filename.")
    if not resolved_rpm:
        raise ValueError("rpm is unknown. Pass --rpm or use a standard dataset filename.")

    signal = load_signal(mat_file, signal_key)
    total_windows = 1 + max(0, (len(signal) - window_size) // stride)
    if max_windows is not None:
        total_windows = min(total_windows, max_windows)

    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as file:
        for window_index in range(total_windows):
            start = window_index * stride
            end = start + window_size
            window = signal[start:end]
            if len(window) < window_size:
                break

            timestamp = start_time + timedelta(seconds=start / resolved_sampling_rate)
            payload: dict[str, Any] = {
                "equipmentId": equipment_id,
                "timestamp": iso_z(timestamp),
                "samplingRate": int(resolved_sampling_rate),
                "rpm": int(resolved_rpm),
                "windowSize": int(window_size),
                "windowIndex": int(window_index),
                "values": np.round(window, decimals=decimals).tolist(),
            }

            if include_label:
                payload["label"] = {
                    "rotatingCondition": metadata.get("rotatingCondition"),
                    "bearingCondition": metadata.get("bearingCondition"),
                    "bearingModel": metadata.get("bearingModel"),
                }

            file.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))
            file.write("\n")

    return total_windows


def main() -> None:
    parser = argparse.ArgumentParser(description="Convert vibration MAT Data array to JSONL windows.")
    parser.add_argument("--input", required=True, type=Path, help="Input .mat file.")
    parser.add_argument("--output", required=True, type=Path, help="Output .jsonl path.")
    parser.add_argument("--equipment-id", default="MOTOR_001")
    parser.add_argument("--signal-key", default=None, help="MAT field name. Defaults to Data/common numeric signal.")
    parser.add_argument("--sampling-rate", type=int, default=None, help="Overrides sampling rate parsed from filename.")
    parser.add_argument("--rpm", type=int, default=None, help="Overrides RPM parsed from filename.")
    parser.add_argument("--window-size", type=int, default=2048)
    parser.add_argument("--stride", type=int, default=None, help="Defaults to --window-size.")
    parser.add_argument("--max-windows", type=int, default=None)
    parser.add_argument("--start-time", default=None, help="ISO timestamp. Example: 2026-05-06T12:00:00Z")
    parser.add_argument("--decimals", type=int, default=8)
    parser.add_argument(
        "--include-label",
        action="store_true",
        help="Include filename-derived labels. Use only for training/debug files, not MQTT replay.",
    )
    args = parser.parse_args()

    mat_file = args.input
    stride = args.stride or args.window_size
    count = write_jsonl(
        mat_file=mat_file,
        output=args.output,
        equipment_id=args.equipment_id,
        signal_key=args.signal_key,
        sampling_rate=args.sampling_rate,
        rpm=args.rpm,
        window_size=args.window_size,
        stride=stride,
        max_windows=args.max_windows,
        start_time=parse_start_time(args.start_time),
        decimals=args.decimals,
        include_label=args.include_label,
    )
    print(f"wrote {count} windows to {args.output}")


if __name__ == "__main__":
    main()
