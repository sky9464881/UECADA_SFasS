package com.example.phm.vibration.dto;

import java.time.Instant;

public record VibrationWindowLatestResponse(
        boolean received,
        long receivedCount,
        Instant lastReceivedAt,
        VibrationWindowSummaryResponse latest
) {
    public static VibrationWindowLatestResponse empty() {
        return new VibrationWindowLatestResponse(false, 0, null, null);
    }
}
