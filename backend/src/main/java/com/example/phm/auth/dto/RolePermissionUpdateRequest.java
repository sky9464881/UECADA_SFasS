package com.example.phm.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RolePermissionUpdateRequest(
        @NotBlank String roleName,
        @NotBlank String permissionId,
        boolean allowed
) {}
