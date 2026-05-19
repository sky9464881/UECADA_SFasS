package com.example.phm.community.dto;

import java.time.Instant;

public record FactoryReportResponse(
        Instant generatedAt,
        String title,
        String markdown
) {
}
