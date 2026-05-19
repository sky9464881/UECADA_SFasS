package com.example.phm.community.repository;

import java.util.List;

import com.example.phm.community.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop100ByChatRoomIdAndDeletedFalseOrderBySentAtAsc(Long chatRoomId);

    boolean existsByMessageContentAndDeletedFalse(String messageContent);

    @Query("""
            SELECT COUNT(m)
            FROM ChatMessage m
            WHERE m.chatRoomId = :chatRoomId
              AND m.deleted = false
              AND m.senderUserId <> :userId
              AND (:lastReadMessageId IS NULL OR m.messageId > :lastReadMessageId)
            """)
    long countUnread(
            @Param("chatRoomId") Long chatRoomId,
            @Param("userId") String userId,
            @Param("lastReadMessageId") Long lastReadMessageId
    );
}
