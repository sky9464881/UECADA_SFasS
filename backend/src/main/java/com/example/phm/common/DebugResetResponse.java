package com.example.phm.common;

public record DebugResetResponse(
        long deletedAlarmRows,
        long deletedAnalysisRows,
        long deletedVibrationWindowRows,
        long deletedRawWindowFiles
) {
}
