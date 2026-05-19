package com.example.phm.community.dto;

import java.util.List;

public record LineGroupResponse(
        String lineId,
        String lineName,
        List<UserBrief> managers,
        List<UserBrief> operators
) {
    public record UserBrief(
            String userId,
            String loginId,
            String userName,
            String roleName,
            String lineId
    ) {
    }
}
