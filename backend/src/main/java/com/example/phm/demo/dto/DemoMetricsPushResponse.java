package com.example.phm.demo.dto;

public record DemoMetricsPushResponse(
        int linesUpdated,
        int equipmentsUpdated,
        long serverTimeMs
) {
}
