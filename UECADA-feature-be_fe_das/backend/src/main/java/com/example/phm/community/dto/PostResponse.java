package com.example.phm.community.dto;

import java.time.LocalDateTime;

import com.example.phm.community.entity.BoardPost;

public record PostResponse(
        Long postId,
        String authorUserId,
        String title,
        String category,
        LocalDateTime createdAt
) {
    public static PostResponse from(BoardPost post) {
        return new PostResponse(
                post.getPostId(),
                post.getAuthorUserId(),
                post.getTitle(),
                post.getCategory(),
                post.getCreatedAt()
        );
    }
}
