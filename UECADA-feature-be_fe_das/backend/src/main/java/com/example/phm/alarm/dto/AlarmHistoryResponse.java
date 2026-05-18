package com.example.phm.alarm.dto;

import java.time.LocalDateTime;

import com.example.phm.alarm.entity.AlarmHistory;
import com.example.phm.analysis.entity.AnalysisResult;

public record AlarmHistoryResponse(
        Long id,
        String equipmentCode,
        Long analysisResultId,
        String alarmLevel,
        String status,
        String message,
        LocalDateTime occurredAt,
        LocalDateTime endedAt,
        Long durationSeconds,
        Double anomalyScore,
        Double rms,
        Double peakToPeak,
        Double kurtosis,
        String prediction
) {

    public static AlarmHistoryResponse from(AlarmHistory alarmHistory) {
        AnalysisResult analysisResult = alarmHistory.getAnalysisResult();
        return new AlarmHistoryResponse(
                alarmHistory.getId(),
                alarmHistory.getEquipmentCode(),
                analysisResult.getId(),
                alarmHistory.getAlarmLevel(),
                alarmHistory.getStatus(),
                alarmHistory.getMessage(),
                alarmHistory.getOccurredAt(),
                alarmHistory.getEndedAt(),
                alarmHistory.getDurationSeconds(),
                analysisResult.getAnomalyScore(),
                analysisResult.getRms(),
                analysisResult.getPeakToPeak(),
                analysisResult.getKurtosis(),
                analysisResult.getPrediction()
        );
    }
}
