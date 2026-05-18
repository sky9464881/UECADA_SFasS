package com.example.phm.alarm.dto;

import java.time.LocalDateTime;

import com.example.phm.alarm.entity.Alarm;

public record AlarmResponse(
        Long alarmId,
        String equipmentCode,
        String alarmType,
        String severity,
        String status,
        String alarmMessage,
        LocalDateTime occurredAt,
        String resolvedBy,
        LocalDateTime resolvedAt,
        String comment
) {
    public static AlarmResponse from(Alarm a) {
        return new AlarmResponse(
                a.getAlarmId(),
                a.getEquipmentCode(),
                a.getAlarmType(),
                a.getSeverity(),
                a.getStatus(),
                a.getAlarmMessage(),
                a.getOccurredAt(),
                a.getResolvedBy(),
                a.getResolvedAt(),
                a.getComment()
        );
    }
}
