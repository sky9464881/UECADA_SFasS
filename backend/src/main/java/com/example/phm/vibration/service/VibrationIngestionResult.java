package com.example.phm.vibration.service;

import com.example.phm.analysis.dto.AnalyzeResponse;
<<<<<<< HEAD
import com.example.phm.analysis.entity.AnalysisResult;
import com.example.phm.vibration.entity.VibrationWindow;

public record VibrationIngestionResult(
        VibrationWindow vibrationWindow,
        AnalysisResult analysisResult,
        boolean alarmCreated,
        String rawFilePath,
=======

public record VibrationIngestionResult(
        Long vibrationWindowId,
        Long analysisResultId,
        boolean alarmCreated,
        boolean rawWindowSaved,
>>>>>>> feature/develop_before
        AnalyzeResponse analysis
) {
}
