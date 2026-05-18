package com.example.phm.analysis.dto;

import java.time.LocalDateTime;

import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.vibration.entity.VibrationWindow;

public record AnalysisResultResponse(
        Long id,
        Long vibrationWindowId,
        String equipmentCode,
        String analysisType,
        String resultJson,
        LocalDateTime measuredAt,
        Double rms,
        Double peakFrequency,
        Double peakToPeak,
        Double crestFactor,
        Double kurtosis,
        String prediction,
        Double confidence,
        String modelVersion,
        String modelInputType,
        Integer modelInputSize,
        Integer modelExpectedInputSize,
        String modelInputStrategy,
        String modelStatus,
        Double anomalyScore,
        String alarmLevel,
        LocalDateTime createdAt
) {

    public static AnalysisResultResponse from(AnalysisResult r) {
        VibrationWindow w = r.getVibrationWindow();
        return new AnalysisResultResponse(
                r.getId(),
                w != null ? w.getId() : null,
                r.getEquipmentCode(),
                r.getAnalysisType(),
                r.getResultJson(),
                w != null ? w.getMeasuredAt() : null,
                r.getRms(),
                r.getPeakFrequency(),
                r.getPeakToPeak(),
                r.getCrestFactor(),
                r.getKurtosis(),
                r.getPrediction(),
                r.getConfidence(),
                r.getModelVersion(),
                r.getModelInputType(),
                r.getModelInputSize(),
                r.getModelExpectedInputSize(),
                r.getModelInputStrategy(),
                r.getModelStatus(),
                r.getAnomalyScore(),
                r.getAlarmLevel(),
                r.getCreatedAt()
        );
    }
}
