package com.example.phm.vibration.service;

import com.example.phm.analysis.dto.AnalyzeResponse;
import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.vibration.entity.VibrationWindow;

public record VibrationIngestionResult(
        VibrationWindow vibrationWindow,
        AnalysisResult analysisResult,
        boolean alarmCreated,
        String rawFilePath,
        AnalyzeResponse analysis
) {
}
