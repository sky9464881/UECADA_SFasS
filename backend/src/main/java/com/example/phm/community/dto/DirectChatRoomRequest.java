package com.example.phm.community.dto;

import jakarta.validation.constraints.NotBlank;

public record DirectChatRoomRequest(
        @NotBlank String requesterUserId,
        @NotBlank String targetUserId
) {
}
