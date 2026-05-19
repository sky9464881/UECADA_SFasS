package com.example.phm.community.repository;

import java.util.List;

import com.example.phm.community.entity.BoardPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    List<BoardPost> findByCategoryAndDeletedFalseOrderByCreatedAtDesc(String category);

    List<BoardPost> findByTargetLineIdAndDeletedFalseOrderByCreatedAtDesc(String targetLineId);

    List<BoardPost> findByDeletedFalseOrderByCreatedAtDesc();

    boolean existsByTitleAndDeletedFalse(String title);
}
