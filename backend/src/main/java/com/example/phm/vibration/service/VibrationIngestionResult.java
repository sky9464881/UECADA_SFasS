package com.example.phm.vibration.service;

import com.example.phm.analysis.dto.AnalyzeResponse;

public record VibrationIngestionResult(
        Long vibrationWindowId,
        Long analysisResultId,
        boolean alarmCreated,
        boolean rawWindowSaved,
        AnalyzeResponse analysis
) {
}
