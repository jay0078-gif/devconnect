package com.devconnect.backend.controller;

import com.devconnect.backend.dto.PostDto;
import com.devconnect.backend.service.FollowService;
import com.devconnect.backend.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final PostService postService;

    // Follow a user
    @PostMapping("/{userId}")
    public ResponseEntity<String> follow(
            Authentication authentication,
            @PathVariable Long userId) {
        return ResponseEntity.ok(
                followService.follow(authentication.getName(), userId));
    }

    // Unfollow a user
    @DeleteMapping("/{userId}")
    public ResponseEntity<String> unfollow(
            Authentication authentication,
            @PathVariable Long userId) {
        return ResponseEntity.ok(
                followService.unfollow(authentication.getName(), userId));
    }

    // Get personalized feed from Redis sorted set
    @GetMapping("/feed")
    public ResponseEntity<List<PostDto>> getFeed(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<Long> postIds = followService.getFeedPostIds(
                authentication.getName(), page, size);

        List<PostDto> posts = postIds.stream()
                .map(postService::getPostById)
                .toList();

        return ResponseEntity.ok(posts);
    }

    // Get follower/following counts for a user
    @GetMapping("/{userId}/stats")
    public ResponseEntity<Map<String, Long>> getStats(
            @PathVariable Long userId) {
        return ResponseEntity.ok(Map.of(
                "followers", followService.getFollowerCount(userId),
                "following", followService.getFollowingCount(userId)
        ));
    }
}