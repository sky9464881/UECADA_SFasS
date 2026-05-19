package com.example.phm.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank String userId,
        @NotBlank String loginId,
        String lineId,
        @NotBlank String userName,
        String email,
        @NotBlank String roleName,
        @NotBlank String password,
        @NotBlank String securityQuestion,
        @NotBlank String securityAnswer
) {}
