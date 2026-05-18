package com.example.phm.operation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.phm.operation.entity.OperationLog;

public record OperationLogResponse(
        Long operationLogId,
        String equipmentCode,
        String statusCode,
        LocalDateTime startAt,
        LocalDateTime endAt,
        BigDecimal durationMin
) {
    public static OperationLogResponse from(OperationLog log) {
        return new OperationLogResponse(
                log.getOperationLogId(),
                log.getEquipmentCode(),
                log.getStatusCode(),
                log.getStartAt(),
                log.getEndAt(),
                log.getDurationMin()
        );
    }
}
