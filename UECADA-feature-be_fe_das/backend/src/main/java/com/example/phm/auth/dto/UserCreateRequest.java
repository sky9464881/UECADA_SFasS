package com.example.phm.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank String userId,
        @NotBlank String loginId,
        @NotBlank String userName,
        String email,
        @NotBlank String roleName,
        @NotBlank String password
) {}
