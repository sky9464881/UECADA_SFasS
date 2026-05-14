from __future__ import annotations

import numpy as np


def calculate_fft(values: np.ndarray, sampling_rate: int) -> tuple[np.ndarray, np.ndarray]:
    centered = values - np.mean(values)
    frequencies = np.fft.rfftfreq(len(centered), d=1.0 / sampling_rate)
    magnitudes = np.abs(np.fft.rfft(centered)) / len(centered)

    if len(magnitudes) > 2:
        magnitudes[1:-1] *= 2.0

    return frequencies, magnitudes


def peak_frequency(frequencies: np.ndarray, magnitudes: np.ndarray) -> float:
    if len(frequencies) <= 1:
        return 0.0

    peak_index = int(np.argmax(magnitudes[1:]) + 1)
    return float(frequencies[peak_index])
