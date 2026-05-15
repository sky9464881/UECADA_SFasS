package com.example.phm.auth.dto;

import java.time.LocalDateTime;

import com.example.phm.auth.entity.User;

public record UserResponse(
        String userId,
        String userName,
        String email,
        String roleName,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getUserName(),
                user.getEmail(),
                user.getRoleName(),
                user.getCreatedAt()
        );
    }
}
