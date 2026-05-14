from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.db.models import AnalysisResult
from app.schemas.vibration_schema import AnalyzeResponse, VibrationWindowRequest
from app.services.feature_service import calculate_features, classify_alarm_level, estimate_anomaly_score
from app.services.predict_service import get_fault_model_service

router = APIRouter()


@router.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: VibrationWindowRequest, db: Session = Depends(get_db)) -> AnalyzeResponse:
    features, fft = calculate_features(request.values, request.samplingRate)
    prediction = get_fault_model_service().predict(request.values, request.samplingRate, request.spectrogram)
    anomaly_score = estimate_anomaly_score(features)
    alarm_level = classify_alarm_level(anomaly_score)

    measured_at = datetime.fromisoformat(request.timestamp.replace("Z", "+00:00")).astimezone(timezone.utc).replace(tzinfo=None)

    result = AnalysisResult(
        equipment_code=request.equipmentId,
        rms=features.rms,
        peak_frequency=features.peakFrequency,
        peak_to_peak=features.peakToPeak,
        crest_factor=features.crestFactor,
        kurtosis=features.kurtosis,
        prediction=prediction.prediction,
        confidence=prediction.confidence,
        model_version=prediction.model_version,
        model_input_type=prediction.input_type,
        model_input_size=prediction.input_size,
        model_expected_input_size=prediction.expected_input_size,
        model_input_strategy=prediction.input_strategy,
        model_status=prediction.status,
        anomaly_score=anomaly_score,
        alarm_level=alarm_level,
    )
    db.add(result)
    db.commit()
    db.refresh(result)

    return AnalyzeResponse(
        analysisResultId=result.id,
        equipmentId=request.equipmentId,
        timestamp=request.timestamp,
        samplingRate=request.samplingRate,
        rpm=request.rpm,
        windowSize=request.windowSize,
        windowIndex=request.windowIndex,
        features=features,
        fft=fft,
        anomalyScore=anomaly_score,
        alarmLevel=alarm_level,
        prediction=prediction.prediction,
        confidence=prediction.confidence,
        modelVersion=prediction.model_version,
        modelInputType=prediction.input_type,
        modelInputSize=prediction.input_size,
        modelExpectedInputSize=prediction.expected_input_size,
        modelInputStrategy=prediction.input_strategy,
        modelStatus=prediction.status,
    )
