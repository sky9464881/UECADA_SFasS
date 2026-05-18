package com.example.phm.dashboard.dto;

import java.util.List;

public record DashboardFrontendResponse(
        Double factoryOee,
        StatusDonut statusDonut,
        AlarmSummary alarmSummary,
        List<LineStat> lineStats,
        List<OeeHourlySeries> oeeHourlySeries
) {

    public record StatusDonut(
            long running,
            long standby,
            long alarm,
            long maintenance,
            long total
    ) {
    }

    public record AlarmSummary(
            long total,
            long critical,
            long warning,
            long resolved,
            long open
    ) {
    }

    public record LineStat(String lineId, String lineName, Double oee) {
    }

    public record OeeHourlySeries(String lineId, String lineName, List<OeePoint> data) {
    }

    public record OeePoint(String time, Double oee) {
    }
}
