package com.example.phm.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record FindLoginIdRequest(
        @NotBlank String userName,
        @NotBlank String email,
        @NotBlank String securityAnswer
) {
}
