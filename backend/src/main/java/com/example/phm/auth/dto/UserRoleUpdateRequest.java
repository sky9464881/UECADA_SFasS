package com.example.phm.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UserRoleUpdateRequest(
        @NotBlank String roleName,
        String lineId
) {}
