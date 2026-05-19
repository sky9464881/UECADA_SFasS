package com.example.phm.community.repository;

import java.util.List;
import java.util.Optional;

import com.example.phm.community.entity.ChatRoomReadState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomReadStateRepository extends JpaRepository<ChatRoomReadState, Long> {

    List<ChatRoomReadState> findByUserId(String userId);

    Optional<ChatRoomReadState> findByChatRoomIdAndUserId(Long chatRoomId, String userId);
}
