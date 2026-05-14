package com.example.phm.auth.dto;

import com.example.phm.auth.entity.User;

public record LoginResponse(
        String userId,
        String userName,
        String email,
        String roleName
) {
    public static LoginResponse from(User user) {
        return new LoginResponse(user.getUserId(), user.getUserName(), user.getEmail(), user.getRoleName());
    }
}
