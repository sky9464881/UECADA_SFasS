package com.example.phm.vibration.dto;

import java.time.Instant;
import java.util.List;

import com.example.phm.analysis.dto.AnalyzeResponse;

public record VibrationRealtimeResponse(
        boolean received,
        String equipmentId,
        Instant receivedAt,
        VibrationWindowSummaryResponse window,
        List<Double> values,
        AnalyzeResponse analysis
) {
    public static VibrationRealtimeResponse empty(String equipmentId) {
        return new VibrationRealtimeResponse(false, equipmentId, null, null, List.of(), null);
    }
}
