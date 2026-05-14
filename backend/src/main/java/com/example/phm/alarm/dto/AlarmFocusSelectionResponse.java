package com.example.phm.alarm.dto;

import java.time.LocalDateTime;

import com.example.phm.analysis.dto.AnalysisFeatures;
import com.example.phm.analysis.dto.FftSummary;

public record AlarmFocusSelectionResponse(
        Long alarmId,
        String equipmentCode,
        LocalDateTime selectedStart,
        LocalDateTime selectedEnd,
        int sampleCount,
        int originalSampleCount,
        boolean downsampled,
        Integer samplingRate,
        AnalysisFeatures features,
        FftSummary fft,
        Double anomalyScore,
        String alarmLevel,
        String prediction,
        Double confidence,
        String modelVersion,
        String modelInputType,
        Integer modelInputSize,
        Integer modelExpectedInputSize,
        String modelInputStrategy,
        String modelStatus
) {
}
