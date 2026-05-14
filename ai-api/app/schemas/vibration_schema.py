from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, model_validator


class VibrationWindowRequest(BaseModel):
    equipmentId: str = Field(min_length=1)
    timestamp: str = Field(min_length=1)
    samplingRate: int = Field(gt=0)
    rpm: int | None = Field(default=None, ge=0)
    windowSize: int = Field(gt=0)
    windowIndex: int = Field(ge=0)
    values: list[float] = Field(min_length=1)
    spectrogram: list[list[float]] | None = None
    persist: bool = True

    @model_validator(mode="after")
    def validate_window_size(self) -> VibrationWindowRequest:
        if len(self.values) != self.windowSize:
            raise ValueError("windowSize must match len(values)")
        return self


class FeatureResponse(BaseModel):
    rms: float
    peakFrequency: float
    peakToPeak: float
    crestFactor: float
    kurtosis: float


class FftResponse(BaseModel):
    frequencyResolution: float
    binCount: int
    frequencies: list[float]
    magnitudes: list[float]


class AnalyzeResponse(BaseModel):
    analysisResultId: int | None = None
    vibrationWindowId: int | None = None
    rawWindowSaved: bool = False
    alarmCreated: bool = False
    equipmentId: str
    timestamp: str
    samplingRate: int
    rpm: int | None
    windowSize: int
    windowIndex: int
    features: FeatureResponse
    fft: FftResponse
    anomalyScore: float
    alarmLevel: Literal["normal", "warning", "danger"]
    prediction: str
    confidence: float
    modelVersion: str
    modelInputType: str | None = None
    modelInputSize: int | None = None
    modelExpectedInputSize: int | None = None
    modelInputStrategy: str | None = None
    modelStatus: str = "unavailable"
