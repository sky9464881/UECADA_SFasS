package com.example.phm.auth.dto;

import com.example.phm.auth.entity.User;

public record LoginResponse(
        String userId,
<<<<<<< HEAD
        String userName,
        String email,
        String roleName
) {
    public static LoginResponse from(User user) {
        return new LoginResponse(user.getUserId(), user.getUserName(), user.getEmail(), user.getRoleName());
=======
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
>>>>>>> feature/develop_before
    }
}
