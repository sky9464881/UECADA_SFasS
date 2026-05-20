package com.example.phm.auth.dto;

import java.time.LocalDateTime;

import com.example.phm.auth.entity.RolePermission;

public record RolePermissionResponse(
        String roleName,
        String permissionId,
        boolean allowed,
        LocalDateTime updatedAt
) {
    public static RolePermissionResponse from(RolePermission permission) {
        return new RolePermissionResponse(
                permission.getRoleName(),
                permission.getPermissionId(),
                permission.isAllowed(),
                permission.getUpdatedAt()
        );
    }
}
