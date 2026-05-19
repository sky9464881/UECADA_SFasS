package com.example.phm.community.dto;

import java.time.LocalDateTime;

import com.example.phm.community.entity.ChatRoom;

public record ChatRoomResponse(
        Long chatRoomId,
        String lineId,
        String roomName,
        String roomType,
        String userAId,
        String userBId,
        LocalDateTime createdAt,
        long unreadCount
) {
    public static ChatRoomResponse from(ChatRoom room) {
        return from(room, 0L);
    }

    public static ChatRoomResponse from(ChatRoom room, long unreadCount) {
        return new ChatRoomResponse(
                room.getChatRoomId(),
                room.getLineId(),
                room.getRoomName(),
                room.getRoomType(),
                room.getUserAId(),
                room.getUserBId(),
                room.getCreatedAt(),
                unreadCount
        );
    }
}
