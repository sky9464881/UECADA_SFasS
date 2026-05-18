package com.example.phm.line.dto;

import java.util.List;

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
        Double latestOee,
        Double balanceRate,
        Double uph,
        Double upmh,
        Double productivity,
        List<Double> stationUtilization
) {
}
