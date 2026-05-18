package com.example.phm.alarm.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record AlarmCreateRequest(
        @NotBlank String equipmentCode,
        String alarmCode,
        String alarmType,
        String alarmCategory,
        @NotBlank String severity,
        String alarmMessage,
        LocalDateTime occurredAt,
        String sensorSnapshot
) {}
