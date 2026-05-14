from __future__ import annotations

import math

import numpy as np
from scipy.stats import kurtosis as scipy_kurtosis

from app.core.config import settings
from app.schemas.vibration_schema import FeatureResponse, FftResponse
from app.services.fft_service import calculate_fft, peak_frequency


def calculate_features(values: list[float], sampling_rate: int) -> tuple[FeatureResponse, FftResponse]:
    signal = np.asarray(values, dtype=float)
    frequencies, magnitudes = calculate_fft(signal, sampling_rate)

    rms = _safe_float(np.sqrt(np.mean(np.square(signal))))
    peak_to_peak = _safe_float(np.max(signal) - np.min(signal))
    crest_factor = _safe_float(np.max(np.abs(signal)) / rms) if rms > 0 else 0.0
    kurtosis_value = _calculate_kurtosis(signal)
    peak_freq = _safe_float(peak_frequency(frequencies, magnitudes))

    features = FeatureResponse(
        rms=round(rms, 8),
        peakFrequency=round(peak_freq, 8),
        peakToPeak=round(peak_to_peak, 8),
        crestFactor=round(crest_factor, 8),
        kurtosis=round(kurtosis_value, 8),
    )
    display_frequencies, display_magnitudes = _downsample_fft(
        frequencies,
        magnitudes,
        settings.fft_max_bins,
    )
    fft = FftResponse(
        frequencyResolution=round(float(frequencies[1] - frequencies[0]), 8) if len(frequencies) > 1 else 0.0,
        binCount=len(display_frequencies),
        frequencies=np.round(display_frequencies, decimals=8).tolist(),
        magnitudes=np.round(display_magnitudes, decimals=8).tolist(),
    )

    return features, fft


def estimate_anomaly_score(features: FeatureResponse) -> float:
    rms_score = _clamp(features.rms / 0.5)
    peak_to_peak_score = _clamp(features.peakToPeak / 2.0)
    crest_score = _clamp((features.crestFactor - 3.0) / 5.0)
    kurtosis_score = _clamp((features.kurtosis - 3.0) / 7.0)

    score = max(rms_score, peak_to_peak_score, crest_score, kurtosis_score)
    return round(score, 4)


def classify_alarm_level(anomaly_score: float) -> str:
    if anomaly_score >= 0.8:
        return "danger"
    if anomaly_score >= 0.5:
        return "warning"
    return "normal"


def _calculate_kurtosis(signal: np.ndarray) -> float:
    if np.std(signal) == 0:
        return 0.0

    value = scipy_kurtosis(signal, fisher=False, bias=False)
    return _safe_float(value)


def _safe_float(value: float) -> float:
    if math.isnan(value) or math.isinf(value):
        return 0.0
    return float(value)


def _clamp(value: float, lower: float = 0.0, upper: float = 1.0) -> float:
    return max(lower, min(upper, value))


def _downsample_fft(
    frequencies: np.ndarray,
    magnitudes: np.ndarray,
    max_bins: int,
) -> tuple[np.ndarray, np.ndarray]:
    if max_bins <= 0 or len(frequencies) <= max_bins:
        return frequencies, magnitudes

    indices = np.linspace(0, len(frequencies) - 1, num=max_bins, dtype=int)
    return frequencies[indices], magnitudes[indices]
