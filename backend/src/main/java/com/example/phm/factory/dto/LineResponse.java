package com.example.phm.factory.dto;

import java.math.BigDecimal;

public record LineResponse(
        String lineId,
        String lineName,
        String lineStatus,
        String factoryId,
        long equipmentTotal,
        long equipmentRunning,
        long equipmentAlarm,
        long equipmentStandby,
        long openAlarmCount,
        BigDecimal latestOee
) {}
