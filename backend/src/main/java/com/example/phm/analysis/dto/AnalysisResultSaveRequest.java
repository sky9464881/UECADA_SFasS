package com.example.phm.analysis.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalysisResultSaveRequest(
        @NotBlank String equipmentCode,
        @NotBlank String analysisType,
        String resultJson
) {}
