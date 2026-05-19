package com.example.phm.community.dto;

import jakarta.validation.constraints.NotBlank;

public record PostCreateRequest(
        @NotBlank String authorUserId,
        @NotBlank String title,
        @NotBlank String content,
        String category,
        String targetLineId,
        Boolean notice
) {}
