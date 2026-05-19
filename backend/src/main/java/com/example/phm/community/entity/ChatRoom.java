package com.example.phm.community.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_room")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "line_id", nullable = false, length = 20)
    private String lineId;

    @Column(name = "room_name", nullable = false, length = 100)
    private String roomName;

    @Column(name = "room_type", nullable = false, length = 20)
    private String roomType = "LINE";

    @Column(name = "user_a_id", length = 20)
    private String userAId;

    @Column(name = "user_b_id", length = 20)
    private String userBId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (roomType == null || roomType.isBlank()) roomType = "LINE";
    }

    public Long getChatRoomId() { return chatRoomId; }
    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public String getUserAId() { return userAId; }
    public void setUserAId(String userAId) { this.userAId = userAId; }
    public String getUserBId() { return userBId; }
    public void setUserBId(String userBId) { this.userBId = userBId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
