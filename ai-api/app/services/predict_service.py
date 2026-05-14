from __future__ import annotations

import logging
import warnings
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from scipy import ndimage
from scipy import signal as scipy_signal

from app.core.config import settings


logger = logging.getLogger("uvicorn.error")


@dataclass(frozen=True)
class PredictionResult:
    prediction: str
    confidence: float
    model_version: str
    input_type: str
    input_size: int
    expected_input_size: int | None
    input_strategy: str
    status: str


class FaultModelService:
    def __init__(self, model_path: str, model_version: str) -> None:
        self.model_path = Path(model_path)
        self.model_version = model_version
        self.input_type = settings.fault_model_input_type
        self.spectrogram_shape = (
            settings.fault_model_spectrogram_size,
            settings.fault_model_spectrogram_size,
        )
        self.training_sampling_rate: int | None = None
        self.training_window_size: int | None = None
        self.training_window_seconds: float | None = None
        self.class_names: list[str] | None = None
        self.stft_params: dict[str, Any] = {
            "window": settings.fault_model_stft_window,
            "nperseg": settings.fault_model_stft_nperseg,
            "noverlap": settings.fault_model_stft_noverlap,
            "detrend": settings.fault_model_stft_detrend,
            "scaling": settings.fault_model_stft_scaling,
            "mode": settings.fault_model_stft_mode,
        }
        self.model: Any | None = None
        self.expected_input_size: int | None = None
        self.status = "missing"
        self._load_model()

    def predict(
        self,
        values: list[float],
        sampling_rate: int,
        spectrogram: list[list[float]] | None = None,
    ) -> PredictionResult:
        input_size = len(values)
        if self.model is None:
            return self._fallback(input_size)

        try:
            vector, strategy = self._prepare_input(values, sampling_rate, spectrogram)
            prediction = self.model.predict(vector)[0]
            confidence = self._confidence(vector)
            return PredictionResult(
                prediction=str(prediction),
                confidence=round(confidence, 4),
                model_version=self.model_version,
                input_type=self.input_type,
                input_size=input_size,
                expected_input_size=self.expected_input_size,
                input_strategy=strategy,
                status=self.status,
            )
        except Exception:
            logger.exception("Fault model prediction failed")
            return PredictionResult(
                prediction="prediction_error",
                confidence=0.0,
                model_version=self.model_version,
                input_type=self.input_type,
                input_size=input_size,
                expected_input_size=self.expected_input_size,
                input_strategy="failed",
                status="error",
            )

    def _load_model(self) -> None:
        if not self.model_path.exists():
            logger.warning("Fault model file not found path=%s", self.model_path)
            return

        with warnings.catch_warnings(record=True) as captured:
            warnings.simplefilter("always")
            loaded = joblib.load(self.model_path)

        for warning in captured:
            logger.warning("Fault model load warning: %s", warning.message)

        self._apply_loaded_artifact(loaded)
        self.expected_input_size = _extract_expected_input_size(self.model)
        if self.expected_input_size is None and self.input_type == "spectrogram":
            self.expected_input_size = self.spectrogram_shape[0] * self.spectrogram_shape[1]
        self.status = "loaded"
        logger.info(
            "Fault model loaded path=%s version=%s type=%s inputType=%s spectrogramShape=%s expectedInputSize=%s",
            self.model_path,
            self.model_version,
            type(self.model).__name__,
            self.input_type,
            self.spectrogram_shape,
            self.expected_input_size,
        )

    def _apply_loaded_artifact(self, loaded: Any) -> None:
        if isinstance(loaded, dict) and "model" in loaded:
            self.model = loaded["model"]
            metadata = loaded.get("metadata")
            if not isinstance(metadata, dict):
                metadata = {}

            def artifact_value(key: str) -> Any:
                return loaded.get(key, metadata.get(key))

            artifact_version = artifact_value("model_version")
            if artifact_version:
                self.model_version = str(artifact_version)
            artifact_input_type = artifact_value("input_type")
            if artifact_input_type:
                self.input_type = str(artifact_input_type)
            artifact_spectrogram_size = artifact_value("spectrogram_size")
            if artifact_spectrogram_size:
                size = int(artifact_spectrogram_size)
                self.spectrogram_shape = (size, size)
            artifact_spectrogram_shape = artifact_value("spectrogram_shape")
            if artifact_spectrogram_shape:
                self.spectrogram_shape = _parse_shape(artifact_spectrogram_shape)
            self.training_sampling_rate = _optional_int(artifact_value("sampling_rate"))
            self.training_window_size = _optional_int(artifact_value("window_size"))
            self.training_window_seconds = _optional_float(artifact_value("window_seconds"))
            class_names = artifact_value("class_names")
            if class_names:
                self.class_names = [str(name) for name in class_names]
            artifact_stft_params = artifact_value("stft_params")
            if isinstance(artifact_stft_params, dict):
                self.stft_params = _merge_stft_params(self.stft_params, artifact_stft_params)
            return

        self.model = loaded

    def _prepare_input(
        self,
        values: list[float],
        sampling_rate: int,
        spectrogram: list[list[float]] | None,
    ) -> tuple[np.ndarray, str]:
        samples = np.asarray(values, dtype=float)
        if samples.size == 0:
            raise ValueError("model input values must not be empty")

        if self.input_type == "spectrogram":
            if spectrogram:
                vector = _spectrogram_to_vector(np.asarray(spectrogram, dtype=float), self.spectrogram_shape)
                rows, cols = self.spectrogram_shape
                return vector.reshape(1, -1), f"payload_spectrogram_{rows}x{cols}"

            vector = _signal_to_spectrogram_vector(samples, sampling_rate, self.spectrogram_shape, self.stft_params)
            rows, cols = self.spectrogram_shape
            return vector.reshape(1, -1), f"stft_spectrogram_{rows}x{cols}_from_raw"

        target_size = self.expected_input_size or samples.size
        if samples.size == target_size:
            return samples.reshape(1, -1), "raw"

        resized = _resample_signal(samples, target_size)
        return resized.reshape(1, -1), f"resampled_{samples.size}_to_{target_size}"

    def _confidence(self, vector: np.ndarray) -> float:
        if hasattr(self.model, "predict_proba"):
            probabilities = self.model.predict_proba(vector)[0]
            return float(np.max(probabilities))
        return 0.0

    def _fallback(self, input_size: int) -> PredictionResult:
        return PredictionResult(
            prediction="not_trained",
            confidence=0.0,
            model_version="signal-features-v0",
            input_type=self.input_type,
            input_size=input_size,
            expected_input_size=None,
            input_strategy="not_available",
            status=self.status,
        )


def _extract_expected_input_size(model: Any) -> int | None:
    direct_size = getattr(model, "n_features_in_", None)
    if direct_size is not None:
        return int(direct_size)

    named_steps = getattr(model, "named_steps", None)
    if named_steps:
        for step in named_steps.values():
            step_size = getattr(step, "n_features_in_", None)
            if step_size is not None:
                return int(step_size)

    return None


def _resample_signal(signal: np.ndarray, target_size: int) -> np.ndarray:
    if target_size <= 0:
        raise ValueError("target_size must be positive")
    if signal.size == 1:
        return np.full(target_size, signal[0], dtype=float)

    source_x = np.linspace(0.0, 1.0, num=signal.size)
    target_x = np.linspace(0.0, 1.0, num=target_size)
    return np.interp(target_x, source_x, signal)


def _signal_to_spectrogram_vector(
    samples: np.ndarray,
    sampling_rate: int,
    target_shape: tuple[int, int],
    stft_params: dict[str, Any],
) -> np.ndarray:
    requested_nperseg = int(stft_params.get("nperseg", 256))
    nperseg = min(requested_nperseg, samples.size)
    if nperseg < 8:
        padded = np.pad(samples, (0, 8 - samples.size), mode="edge")
        nperseg = 8
    else:
        padded = samples

    requested_noverlap = int(stft_params.get("noverlap", nperseg // 2))
    noverlap = min(max(0, requested_noverlap), nperseg - 1)
    _, _, spectrogram = scipy_signal.spectrogram(
        padded,
        fs=float(sampling_rate),
        window=stft_params.get("window", "hann"),
        nperseg=nperseg,
        noverlap=noverlap,
        detrend=stft_params.get("detrend", False),
        scaling=stft_params.get("scaling", "spectrum"),
        mode=stft_params.get("mode", "magnitude"),
    )
    return _spectrogram_to_vector(spectrogram, target_shape)


def _spectrogram_to_vector(spectrogram: np.ndarray, target_shape: tuple[int, int]) -> np.ndarray:
    matrix = np.asarray(spectrogram, dtype=float)
    if matrix.ndim != 2:
        raise ValueError("spectrogram must be a 2D array")
    if matrix.size == 0:
        raise ValueError("spectrogram must not be empty")

    matrix = np.nan_to_num(matrix, nan=0.0, posinf=0.0, neginf=0.0)
    target_rows, target_cols = target_shape
    if target_rows <= 0 or target_cols <= 0:
        raise ValueError("spectrogram target shape must be positive")
    zoom = (target_rows / matrix.shape[0], target_cols / matrix.shape[1])
    resized = ndimage.zoom(matrix, zoom, order=1)
    return resized.reshape(-1)


def _parse_shape(value: Any) -> tuple[int, int]:
    if isinstance(value, int):
        return value, value
    if isinstance(value, (list, tuple)) and len(value) == 2:
        rows = int(value[0])
        cols = int(value[1])
        if rows > 0 and cols > 0:
            return rows, cols
    raise ValueError("spectrogram_shape must be an integer or a two-item list")


def _optional_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    return int(value)


def _optional_float(value: Any) -> float | None:
    if value is None or value == "":
        return None
    return float(value)


def _merge_stft_params(defaults: dict[str, Any], overrides: dict[str, Any]) -> dict[str, Any]:
    merged = dict(defaults)
    allowed_keys = {"window", "nperseg", "noverlap", "detrend", "scaling", "mode"}
    for key, value in overrides.items():
        if key in allowed_keys and value is not None:
            merged[key] = value
    return merged


@lru_cache(maxsize=1)
def get_fault_model_service() -> FaultModelService:
    return FaultModelService(settings.fault_model_path, settings.fault_model_version)
