package com.example.phm.community.dto;

import jakarta.validation.constraints.NotBlank;

public record PostCreateRequest(
        @NotBlank String authorUserId,
        @NotBlank String title,
        @NotBlank String content,
<<<<<<< HEAD
        String category
=======
        String category,
        String targetLineId,
        Boolean notice
>>>>>>> feature/develop_before
) {}
