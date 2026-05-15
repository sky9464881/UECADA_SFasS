package com.example.phm.operation.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record OperationLogCreateRequest(
        @NotBlank String equipmentCode,
        @NotBlank String statusCode,
        LocalDateTime startAt,
        LocalDateTime endAt
) {}
