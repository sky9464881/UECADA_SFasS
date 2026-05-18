package com.example.phm.vibration.dto;

public record VibrationWindowSummaryResponse(
        String equipmentId,
        String timestamp,
        Integer samplingRate,
        Integer rpm,
        Integer windowSize,
        Integer windowIndex,
        int valuesLength
) {
    public static VibrationWindowSummaryResponse from(VibrationWindowMessage message) {
        return new VibrationWindowSummaryResponse(
                message.getEquipmentId(),
                message.getTimestamp(),
                message.getSamplingRate(),
                message.getRpm(),
                message.getWindowSize(),
                message.getWindowIndex(),
                message.valuesLength()
        );
    }
}
