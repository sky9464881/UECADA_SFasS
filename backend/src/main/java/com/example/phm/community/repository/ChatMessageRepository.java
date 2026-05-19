package com.example.phm.community.repository;

import java.util.List;

import com.example.phm.community.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop100ByChatRoomIdAndDeletedFalseOrderBySentAtAsc(Long chatRoomId);
}
