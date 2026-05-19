package com.example.phm.community.dto;

import java.time.LocalDateTime;

import com.example.phm.community.entity.BoardPost;

public record PostResponse(
        Long postId,
        String authorUserId,
        String title,
<<<<<<< HEAD
        String category,
=======
        String content,
        String category,
        String targetLineId,
        boolean notice,
>>>>>>> feature/develop_before
        LocalDateTime createdAt
) {
    public static PostResponse from(BoardPost post) {
        return new PostResponse(
                post.getPostId(),
                post.getAuthorUserId(),
                post.getTitle(),
<<<<<<< HEAD
                post.getCategory(),
=======
                post.getContent(),
                post.getCategory(),
                post.getTargetLineId(),
                post.isNotice(),
>>>>>>> feature/develop_before
                post.getCreatedAt()
        );
    }
}
