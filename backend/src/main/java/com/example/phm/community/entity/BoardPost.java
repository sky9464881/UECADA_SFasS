package com.example.phm.community.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "board_post")
public class BoardPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "author_user_id", nullable = false, length = 20)
    private String authorUserId;

    @Column(name = "post_title", nullable = false, length = 200)
    private String title;

    @Column(name = "post_content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "post_type", length = 30)
    private String category;

<<<<<<< HEAD
=======
    @Column(name = "target_line_id", length = 20)
    private String targetLineId;

>>>>>>> feature/develop_before
    @Column(name = "is_notice", nullable = false)
    private boolean notice = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getPostId() { return postId; }

    public String getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(String authorUserId) { this.authorUserId = authorUserId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

<<<<<<< HEAD
=======
    public String getTargetLineId() { return targetLineId; }
    public void setTargetLineId(String targetLineId) { this.targetLineId = targetLineId; }

>>>>>>> feature/develop_before
    public boolean isNotice() { return notice; }
    public void setNotice(boolean notice) { this.notice = notice; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
