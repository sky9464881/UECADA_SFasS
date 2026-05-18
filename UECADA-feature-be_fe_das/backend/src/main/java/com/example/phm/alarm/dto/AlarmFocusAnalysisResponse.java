package com.example.phm.alarm.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AlarmFocusAnalysisResponse(
        Long alarmId,
        String equipmentCode,
        String alarmLevel,
        String status,
        LocalDateTime occurredAt,
        LocalDateTime endedAt,
        LocalDateTime rangeStart,
        LocalDateTime rangeEnd,
        Integer samplingRate,
        int windowCount,
        int sampleCount,
        int originalSampleCount,
        boolean downsampled,
        Long firstWindowIndex,
        Long lastWindowIndex,
        List<FocusRawPoint> points,
        List<FocusAnalysisPoint> analysisTrend
) {

    public record FocusRawPoint(
            double timestamp,
            double value,
            Long windowIndex
    ) {
    }

    public record FocusAnalysisPoint(
            Long analysisResultId,
            Long vibrationWindowId,
            LocalDateTime measuredAt,
            Long windowIndex,
            Double rms,
            Double peakFrequency,
            Double peakToPeak,
            Double crestFactor,
            Double kurtosis,
            Double anomalyScore,
            String alarmLevel,
            String prediction,
            Double confidence
    ) {
    }
}
