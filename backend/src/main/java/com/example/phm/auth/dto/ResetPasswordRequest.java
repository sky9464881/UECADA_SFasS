package com.example.phm.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String loginId,
        @NotBlank String securityAnswer,
        @NotBlank String newPassword
) {
}
