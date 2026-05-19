package com.example.phm.community.dto;

import java.time.LocalDateTime;

import com.example.phm.community.entity.ChatMessage;

public record ChatMessageResponse(
        Long messageId,
        Long chatRoomId,
        String senderUserId,
        String messageContent,
        LocalDateTime sentAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getMessageId(),
                message.getChatRoomId(),
                message.getSenderUserId(),
                message.getMessageContent(),
                message.getSentAt()
        );
    }
}
