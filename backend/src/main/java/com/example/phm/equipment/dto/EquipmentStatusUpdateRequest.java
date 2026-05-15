package com.example.phm.equipment.dto;

import jakarta.validation.constraints.NotBlank;

public record EquipmentStatusUpdateRequest(@NotBlank String statusCode) {}
