package com.devconnect.backend.service;

import com.devconnect.backend.dto.PostDto;
import com.devconnect.backend.entity.Post;
import com.devconnect.backend.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostQueryService {

    private final PostRepository postRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // ── QUERY: Get single post ──
    // Check Redis first (written there by CommandService)
    // Fall back to MySQL only on miss
    @Cacheable(value = "posts", key = "#id")
    public PostDto getPostById(Long id) {
        log.info("QUERY — cache miss, fetching post {} from MySQL", id);
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        return mapToDto(post);
    }

    // ── QUERY: Get paginated global feed ──
    // Pure MySQL — changes too fast to cache globally
    public Page<PostDto> getFeed(int page, int size) {
        log.info("QUERY — fetching global feed page {}", page);
        return postRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(this::mapToDto);
    }

    // ── QUERY: Get posts by a specific user ──
    public Page<PostDto> getPostsByUser(Long userId, int page, int size) {
        log.info("QUERY — fetching posts for user {}", userId);
        return postRepository
                .findByAuthorIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::mapToDto);
    }

    // ── QUERY: Get personalized feed from Redis sorted set ──
    // Post IDs come from FollowService fan-out
    // We hydrate each ID into a PostDto here
    public List<PostDto> getPersonalizedFeed(List<Long> postIds) {
        log.info("QUERY — hydrating {} post IDs from Redis feed", postIds.size());
        List<PostDto> posts = new ArrayList<>();
        for (Long id : postIds) {
            try {
                posts.add(getPostById(id)); // cache-aside per post
            } catch (Exception e) {
                log.warn("QUERY — post {} not found, skipping", id);
            }
        }
        return posts;
    }

    private PostDto mapToDto(Post post) {
        PostDto dto = new PostDto();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setLikeCount(post.getLikeCount());
        dto.setViewCount(post.getViewCount());
        dto.setAuthorId(post.getAuthor().getId());
        dto.setAuthorUsername(post.getAuthor().getUsername());
        dto.setCreatedAt(post.getCreatedAt());
        return dto;
    }
}