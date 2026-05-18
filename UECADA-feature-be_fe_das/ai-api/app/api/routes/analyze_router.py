from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.db.database import get_db
from app.db.models import AnalysisResult
from app.schemas.vibration_schema import AnalyzeResponse, VibrationWindowRequest
from app.services.feature_service import calculate_features, classify_alarm_level, estimate_anomaly_score
from app.services.persistence_service import (
    ensure_equipment_exists,
    has_open_alarm,
    save_alarm_state_if_needed,
    save_raw_window_if_due,
)
from app.services.predict_service import get_fault_model_service

router = APIRouter()


@router.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: VibrationWindowRequest, db: Session = Depends(get_db)) -> AnalyzeResponse:
    features, fft = calculate_features(request.values, request.samplingRate)
    prediction = get_fault_model_service().predict(request.values, request.samplingRate, request.spectrogram)
    anomaly_score = estimate_anomaly_score(features, prediction.prediction, prediction.status)
    alarm_level = classify_alarm_level(anomaly_score)

    measured_at = datetime.fromisoformat(request.timestamp.replace("Z", "+00:00")).astimezone(timezone.utc).replace(tzinfo=None)
    if not request.persist:
        return AnalyzeResponse(
            analysisResultId=None,
            vibrationWindowId=None,
            rawWindowSaved=False,
            alarmCreated=False,
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

    ensure_equipment_exists(db, request.equipmentId)
    alarm_event = alarm_level in {"warning", "danger"}
    close_event = not alarm_event and has_open_alarm(db, equipment_code=request.equipmentId)
    vibration_window, raw_window_saved = save_raw_window_if_due(
        db,
        equipment_code=request.equipmentId,
        measured_at=measured_at,
        sampling_rate=request.samplingRate,
        rpm=request.rpm,
        window_size=request.windowSize,
        window_index=request.windowIndex,
        values=request.values,
        force=alarm_event,
    )
    should_save_analysis = raw_window_saved or alarm_event or close_event

    result = None
    alarm_created = False
    if should_save_analysis:
        result = AnalysisResult(
            vibration_window_id=vibration_window.id if vibration_window else None,
            equipment_code=request.equipmentId,
            analysis_type="vibration",
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
        db.flush()

        if alarm_event or close_event:
            alarm_created = save_alarm_state_if_needed(
                db,
                analysis_result_id=result.id,
                equipment_code=request.equipmentId,
                alarm_level=alarm_level,
                anomaly_score=anomaly_score,
                prediction=prediction.prediction,
                measured_at=measured_at,
            )

    db.commit()
    if result is not None:
        db.refresh(result)
    return AnalyzeResponse(
        analysisResultId=result.id if result else None,
        vibrationWindowId=vibration_window.id if vibration_window else None,
        rawWindowSaved=raw_window_saved,
        alarmCreated=alarm_created,
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
