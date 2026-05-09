package com.devconnect.backend.controller;

import com.devconnect.backend.dto.CreatePostRequest;
import com.devconnect.backend.dto.PostDto;
import com.devconnect.backend.service.FollowService;
import com.devconnect.backend.service.PostCommandService;
import com.devconnect.backend.service.PostQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    // CQRS: controller routes to the right side
    // Writes → PostCommandService
    // Reads  → PostQueryService
    private final PostCommandService postCommandService;
    private final PostQueryService postQueryService;
    private final FollowService followService;

    // ── COMMAND: Create post ──
    @PostMapping
    public ResponseEntity<PostDto> createPost(
            Authentication auth,
            @RequestBody CreatePostRequest request) {
        return ResponseEntity.ok(
                postCommandService.createPost(auth.getName(), request));
    }

    // ── COMMAND: Like post ──
    @PostMapping("/{id}/like")
    public ResponseEntity<Long> likePost(
            @PathVariable Long id,
            Authentication auth) {
        return ResponseEntity.ok(
                postCommandService.likePost(id, auth.getName()));
    }

    // ── COMMAND: Delete post ──
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            Authentication auth) {
        postCommandService.deletePost(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    // ── QUERY: Get single post ──
    @GetMapping("/{id}")
    public ResponseEntity<PostDto> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(postQueryService.getPostById(id));
    }

    // ── QUERY: Global feed (paginated) ──
    @GetMapping("/feed/global")
    public ResponseEntity<Page<PostDto>> getGlobalFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(postQueryService.getFeed(page, size));
    }

    // ── QUERY: Personalized feed from Redis ──
    // Post IDs come from Redis sorted set (fan-out on write)
    // Then hydrated into PostDtos by PostQueryService
    @GetMapping("/feed/me")
    public ResponseEntity<List<PostDto>> getMyFeed(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Long> postIds = followService.getFeedPostIds(
                auth.getName(), page, size);
        return ResponseEntity.ok(
                postQueryService.getPersonalizedFeed(postIds));
    }

    // ── QUERY: Posts by a specific user ──
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<PostDto>> getPostsByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
                postQueryService.getPostsByUser(userId, page, size));
    }
}