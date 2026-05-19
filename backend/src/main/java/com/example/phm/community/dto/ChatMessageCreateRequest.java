package com.example.phm.community.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageCreateRequest(
        @NotBlank String senderUserId,
        @NotBlank String messageContent
) {
}
