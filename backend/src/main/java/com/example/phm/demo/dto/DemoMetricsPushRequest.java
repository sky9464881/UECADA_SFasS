package com.example.phm.demo.dto;

import java.util.List;

public record DemoMetricsPushRequest(
        List<LinePush> lines,
        List<EquipmentPush> equipments
) {

    public record LinePush(
            String lineId,
            Double balanceRate,
            Double uph,
            Double upmh,
            Double productivity,
            List<Double> stationUtilization
    ) {
    }

    public record EquipmentPush(
            String equipmentCode,
            Double utilizationRate,
            Integer defectCount,
            String operatorName,
            Double cycleTimeSec,
            Double currentAmp,
            Double temperatureC,
            Double humidityPct,
            Double vibrationMmS
    ) {
    }
}
