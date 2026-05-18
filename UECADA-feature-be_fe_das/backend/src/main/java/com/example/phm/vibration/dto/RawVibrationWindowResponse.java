package com.example.phm.vibration.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.phm.vibration.entity.VibrationWindow;

public record RawVibrationWindowResponse(
        Long id,
        String equipmentId,
        LocalDateTime measuredAt,
        LocalDateTime createdAt,
        String timestamp,
        Integer samplingRate,
        Integer rpm,
        Integer windowSize,
        Long windowIndex,
        List<Double> values
) {

    public static RawVibrationWindowResponse from(VibrationWindow vibrationWindow, VibrationWindowMessage message) {
        return from(vibrationWindow, message, true);
    }

    public static RawVibrationWindowResponse from(
            VibrationWindow vibrationWindow,
            VibrationWindowMessage message,
            boolean includeValues
    ) {
        return new RawVibrationWindowResponse(
                vibrationWindow.getId(),
                vibrationWindow.getEquipmentCode(),
                vibrationWindow.getMeasuredAt(),
                vibrationWindow.getCreatedAt(),
                message.getTimestamp(),
                vibrationWindow.getSamplingRate(),
                vibrationWindow.getRpm(),
                vibrationWindow.getWindowSize(),
                vibrationWindow.getWindowIndex(),
                includeValues ? message.getValues() : List.of()
        );
    }
}
