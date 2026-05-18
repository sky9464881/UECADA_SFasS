package com.example.phm.dashboard.dto;

import java.util.List;

public record DashboardSummaryResponse(
        long equipmentCount,
        long recentAnalysisCount,
        long recentAlarmCount,
        List<DistributionItem> equipmentStatusDistribution,
        List<DistributionItem> alarmLevelDistribution
) {

    public record DistributionItem(String name, long value) {
    }
}
