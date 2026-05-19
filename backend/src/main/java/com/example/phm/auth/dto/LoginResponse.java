package com.example.phm.auth.dto;

import com.example.phm.auth.entity.User;

public record LoginResponse(
        String userId,
        String loginId,
        String userName,
        String email,
        String roleName,
        String lineId
) {
    public static LoginResponse from(User user) {
        return new LoginResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getUserName(),
                user.getEmail(),
                user.getRoleName(),
                user.getLineId()
        );
    }
}
