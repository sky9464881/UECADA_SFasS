package com.example.phm.auth.repository;

import java.util.List;
import java.util.Optional;

import com.example.phm.auth.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleName(String roleName);

    Optional<RolePermission> findByRoleNameAndPermissionId(String roleName, String permissionId);
}
