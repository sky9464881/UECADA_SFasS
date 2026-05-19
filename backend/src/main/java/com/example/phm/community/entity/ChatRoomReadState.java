package com.example.phm.community.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "chat_room_read_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_room_read_state_room_user",
                columnNames = {"chat_room_id", "user_id"}
        )
)
public class ChatRoomReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "read_state_id")
    private Long readStateId;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false, length = 20)
    private String userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getReadStateId() { return readStateId; }
    public Long getChatRoomId() { return chatRoomId; }
    public void setChatRoomId(Long chatRoomId) { this.chatRoomId = chatRoomId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(Long lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
