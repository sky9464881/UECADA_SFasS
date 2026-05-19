package com.example.phm.auth.dto;

import java.time.LocalDateTime;

import com.example.phm.auth.entity.User;

public record UserResponse(
        String userId,
<<<<<<< HEAD
        String userName,
        String email,
        String roleName,
=======
        String loginId,
        String userName,
        String email,
        String roleName,
        String lineId,
>>>>>>> feature/develop_before
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
<<<<<<< HEAD
                user.getUserName(),
                user.getEmail(),
                user.getRoleName(),
=======
                user.getLoginId(),
                user.getUserName(),
                user.getEmail(),
                user.getRoleName(),
                user.getLineId(),
>>>>>>> feature/develop_before
                user.getCreatedAt()
        );
    }
}
