package com.example.phm.community.controller;

import java.util.List;

import com.example.phm.community.dto.PostCreateRequest;
import com.example.phm.community.dto.PostResponse;
import com.example.phm.community.entity.BoardPost;
import com.example.phm.community.repository.BoardPostRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class BoardPostController {

    private final BoardPostRepository boardPostRepository;

    public BoardPostController(BoardPostRepository boardPostRepository) {
        this.boardPostRepository = boardPostRepository;
    }

    @GetMapping
<<<<<<< HEAD
    public List<PostResponse> findAll(@RequestParam(required = false) String category) {
        List<BoardPost> posts = category != null && !category.isBlank()
                ? boardPostRepository.findByCategoryAndDeletedFalseOrderByCreatedAtDesc(category)
                : boardPostRepository.findByDeletedFalseOrderByCreatedAtDesc();
=======
    public List<PostResponse> findAll(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String targetLineId
    ) {
        List<BoardPost> posts = targetLineId != null && !targetLineId.isBlank()
                ? boardPostRepository.findByTargetLineIdAndDeletedFalseOrderByCreatedAtDesc(targetLineId)
                : category != null && !category.isBlank()
                    ? boardPostRepository.findByCategoryAndDeletedFalseOrderByCreatedAtDesc(category)
                    : boardPostRepository.findByDeletedFalseOrderByCreatedAtDesc();
>>>>>>> feature/develop_before
        return posts.stream().map(PostResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@Valid @RequestBody PostCreateRequest request) {
        BoardPost post = new BoardPost();
        post.setAuthorUserId(request.authorUserId());
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setCategory(request.category());
<<<<<<< HEAD
=======
        post.setTargetLineId(request.targetLineId());
        post.setNotice(Boolean.TRUE.equals(request.notice()));
>>>>>>> feature/develop_before
        return PostResponse.from(boardPostRepository.save(post));
    }
}
