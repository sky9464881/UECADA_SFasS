package com.example.phm.line.dto;

public record LineResponse(
        String lineId,
        String lineName,
        String lineStatus,
        String factoryId,
        long equipmentTotal,
        long equipmentRunning,
        long equipmentAlarm,
        long equipmentStandby,
        long equipmentMaintenance,
        long openAlarmCount,
        Double latestOee
) {
}
