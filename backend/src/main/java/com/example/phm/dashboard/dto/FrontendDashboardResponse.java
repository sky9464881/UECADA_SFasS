package com.example.phm.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record FrontendDashboardResponse(
        BigDecimal factoryOee,
        StatusDonut statusDonut,
        AlarmSummary alarmSummary,
        List<LineStat> lineStats,
        List<LineOeeSeries> oeeHourlySeries
) {

    public record StatusDonut(
            long running,
            long standby,
            long alarm,
            long maintenance,
            long total
    ) {}

    public record AlarmSummary(
            long total,
            long critical,
            long warning,
            long resolved,
            long open
    ) {}

    public record LineStat(
            String lineId,
            String lineName,
            BigDecimal oee
    ) {}

    public record LineOeeSeries(
            String lineId,
            String lineName,
            List<OeePoint> data
    ) {}

    public record OeePoint(String time, BigDecimal oee) {}
}
