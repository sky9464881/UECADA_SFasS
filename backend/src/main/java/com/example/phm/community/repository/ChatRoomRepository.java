package com.example.phm.community.repository;

import java.util.List;
import java.util.Optional;

import com.example.phm.community.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    List<ChatRoom> findByLineIdOrderByCreatedAtAsc(String lineId);

    List<ChatRoom> findByRoomTypeOrderByCreatedAtAsc(String roomType);

    Optional<ChatRoom> findByRoomTypeAndUserAIdAndUserBId(String roomType, String userAId, String userBId);
}
