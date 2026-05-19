package com.example.phm.community.dto;

import java.time.Instant;

public record FactoryReportResponse(
        Instant generatedAt,
        String reportType,
        String title,
        String markdown
) {
}
