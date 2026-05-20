package com.example.phm.auth.service;

import java.util.List;

import com.example.phm.auth.dto.RolePermissionResponse;
import com.example.phm.auth.dto.RolePermissionUpdateRequest;
import com.example.phm.auth.entity.RolePermission;
import com.example.phm.auth.repository.RolePermissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolePermissionService {

    private final RolePermissionRepository rolePermissionRepository;

    public RolePermissionService(RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public List<RolePermissionResponse> findAll(String roleName) {
        List<RolePermission> rows = roleName == null || roleName.isBlank()
                ? rolePermissionRepository.findAll()
                : rolePermissionRepository.findByRoleName(normalizeRole(roleName));
        return rows.stream().map(RolePermissionResponse::from).toList();
    }

    @Transactional
    public RolePermissionResponse update(RolePermissionUpdateRequest request) {
        String roleName = normalizeRole(request.roleName());
        String permissionId = normalizePermissionId(request.permissionId());
        RolePermission permission = rolePermissionRepository
                .findByRoleNameAndPermissionId(roleName, permissionId)
                .orElseGet(RolePermission::new);
        permission.setRoleName(roleName);
        permission.setPermissionId(permissionId);
        permission.setAllowed(request.allowed());
        return RolePermissionResponse.from(rolePermissionRepository.save(permission));
    }

    private String normalizeRole(String roleName) {
        return roleName == null ? "OPERATOR" : roleName.trim().toUpperCase();
    }

    private String normalizePermissionId(String permissionId) {
        return permissionId == null ? "" : permissionId.trim();
    }
}
