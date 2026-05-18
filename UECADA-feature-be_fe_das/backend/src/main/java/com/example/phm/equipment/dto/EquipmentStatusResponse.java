package com.example.phm.equipment.dto;

import java.time.LocalDateTime;

import com.example.phm.equipment.entity.EquipmentStatus;

public record EquipmentStatusResponse(String equipId, String statusCode, LocalDateTime updatedAt) {
    public static EquipmentStatusResponse from(EquipmentStatus s) {
        return new EquipmentStatusResponse(s.getEquipId(), s.getStatusCode(), s.getUpdatedAt());
    }
}
