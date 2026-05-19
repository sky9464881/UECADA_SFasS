package com.example.phm.auth.dto;

public record SecurityQuestionResponse(
        String loginId,
        String securityQuestion
) {
}
