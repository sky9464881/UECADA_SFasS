package com.example.phm.equipment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.phm.equipment.entity.Equipment;

public record EquipmentResponse(
        Long id,
        String equipmentCode,
        String equipmentName,
        String processType,
        String model,
        LocalDate installDate,
        String location,
        BigDecimal locationX,
        BigDecimal locationY,
        LocalDateTime createdAt,
        Double utilizationRate,
        Integer defectCount,
        String operatorName,
        Double cycleTimeSec,
        Double currentAmp,
        Double temperatureC,
        Double humidityPct,
        Double vibrationMmS
) {

    public static EquipmentResponse from(Equipment equipment) {
        return new EquipmentResponse(
                equipment.getId(),
                equipment.getEquipmentCode(),
                equipment.getEquipmentName(),
                equipment.getProcessType(),
                equipment.getModel(),
                equipment.getInstallDate(),
                equipment.getLocation(),
                equipment.getLocationX(),
                equipment.getLocationY(),
                equipment.getCreatedAt(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public EquipmentResponse withRuntime(
            Double utilizationRate,
            Integer defectCount,
            String operatorName,
            Double cycleTimeSec,
            Double currentAmp,
            Double temperatureC,
            Double humidityPct,
            Double vibrationMmS
    ) {
        return new EquipmentResponse(
                id,
                equipmentCode,
                equipmentName,
                processType,
                model,
                installDate,
                location,
                locationX,
                locationY,
                createdAt,
                utilizationRate,
                defectCount,
                operatorName,
                cycleTimeSec,
                currentAmp,
                temperatureC,
                humidityPct,
                vibrationMmS
        );
    }
}
