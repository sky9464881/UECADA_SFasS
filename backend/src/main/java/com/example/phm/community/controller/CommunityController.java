package com.example.phm.community.controller;

import java.util.List;

import com.example.phm.community.dto.ChatMessageCreateRequest;
import com.example.phm.community.dto.ChatMessageResponse;
import com.example.phm.community.dto.ChatRoomResponse;
import com.example.phm.community.dto.DirectChatRoomRequest;
import com.example.phm.community.dto.FactoryReportResponse;
import com.example.phm.community.dto.LineGroupResponse;
import com.example.phm.community.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/line-groups")
    public List<LineGroupResponse> lineGroups() {
        return communityService.lineGroups();
    }

    @GetMapping("/chat/rooms")
    public List<ChatRoomResponse> rooms(@RequestParam String currentUserId) {
        return communityService.rooms(currentUserId);
    }

    @PostMapping("/chat/rooms/direct")
    public ChatRoomResponse directRoom(@Valid @RequestBody DirectChatRoomRequest request) {
        return communityService.directRoom(request);
    }

    @GetMapping("/chat/rooms/{roomId}/messages")
    public List<ChatMessageResponse> messages(
            @PathVariable Long roomId,
            @RequestParam String currentUserId
    ) {
        return communityService.messages(roomId, currentUserId);
    }

    @PostMapping("/chat/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse send(
            @PathVariable Long roomId,
            @Valid @RequestBody ChatMessageCreateRequest request
    ) {
        return communityService.send(roomId, request);
    }

    @GetMapping("/factory-report")
    public FactoryReportResponse factoryReport() {
        return communityService.factoryReport();
    }
}
