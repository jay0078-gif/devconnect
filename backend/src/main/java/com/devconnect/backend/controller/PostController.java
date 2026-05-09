package com.devconnect.backend.controller;

import com.devconnect.backend.dto.CreatePostRequest;
import com.devconnect.backend.dto.PostDto;
import com.devconnect.backend.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostDto> create(
            Authentication authentication,
            @Valid @RequestBody CreatePostRequest request) {
        String email = authentication.getName();
        return ResponseEntity.ok(postService.createPost(email, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDto> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    @GetMapping("/feed")
    public ResponseEntity<Page<PostDto>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postService.getFeed(page, size));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<PostDto>> getByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postService.getPostsByUser(userId, page, size));
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable Long id) {
        postService.deletePost(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Long> like(
            Authentication authentication,
            @PathVariable Long id) {
        return ResponseEntity.ok(postService.likePost(id, authentication.getName()));
    }
}