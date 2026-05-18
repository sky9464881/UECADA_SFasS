package com.example.phm.vibration.dto;

import java.util.List;

public record RawVibrationSeriesResponse(
        String equipmentId,
        int windowCount,
        int sampleCount,
        int originalSampleCount,
        boolean downsampled,
        Integer samplingRate,
        Long firstWindowIndex,
        Long lastWindowIndex,
        List<RawVibrationPoint> points
) {

    public record RawVibrationPoint(
            double timestamp,
            double value,
            Long windowIndex
    ) {
    }
}
