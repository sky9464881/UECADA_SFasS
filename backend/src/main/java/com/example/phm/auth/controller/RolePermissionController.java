package com.example.phm.auth.controller;

import java.util.List;

import com.example.phm.auth.dto.RolePermissionResponse;
import com.example.phm.auth.dto.RolePermissionUpdateRequest;
import com.example.phm.auth.service.RolePermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/role-permissions")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    public RolePermissionController(RolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    @GetMapping
    public List<RolePermissionResponse> findAll(@RequestParam(required = false) String roleName) {
        return rolePermissionService.findAll(roleName);
    }

    @PatchMapping
    public RolePermissionResponse update(@Valid @RequestBody RolePermissionUpdateRequest request) {
        return rolePermissionService.update(request);
    }
}
