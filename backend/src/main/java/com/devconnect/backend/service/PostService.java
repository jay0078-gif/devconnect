package com.devconnect.backend.service;

import com.devconnect.backend.dto.CreatePostRequest;
import com.devconnect.backend.dto.PostDto;
import com.devconnect.backend.entity.Post;
import com.devconnect.backend.entity.User;
import com.devconnect.backend.repository.PostRepository;
import com.devconnect.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LIKE_KEY_PREFIX = "likes:post:";

    // ─────────────────────────────────────────
    // CREATE POST
    // Write-through: save to MySQL then cache immediately
    // So first GET after create is a cache HIT not miss
    // ─────────────────────────────────────────
    public PostDto createPost(String email, CreatePostRequest request) {
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = new Post();
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setAuthor(author);

        Post saved = postRepository.save(post);
        log.info("Post created by {} with id {}", email, saved.getId());

        PostDto dto = mapToDto(saved);

        // Write-through: cache immediately after save
        // Next GET /posts/{id} = cache HIT, no DB query
        redisTemplate.opsForValue().set("post:" + saved.getId(), dto);
        log.info("Post {} cached in Redis (write-through)", saved.getId());

        return dto;
    }

    // ─────────────────────────────────────────
    // GET POST BY ID — Cache-aside
    // ─────────────────────────────────────────
    @Cacheable(value = "posts", key = "#id")
    public PostDto getPostById(Long id) {
        log.info("CACHE MISS — fetching post {} from MySQL", id);
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        return mapToDto(post);
    }

    // ─────────────────────────────────────────
    // GET FEED — paginated, not cached
    // (feed changes too frequently to cache the whole page)
    // ─────────────────────────────────────────
    public Page<PostDto> getFeed(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return postRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToDto);
    }

    // GET posts by a specific user
    public Page<PostDto> getPostsByUser(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return postRepository.findByAuthorIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToDto);
    }

    // ─────────────────────────────────────────
    // LIKE — Write-behind pattern
    // Redis INCR is atomic — safe under high concurrency
    // Actual DB update happens every 30 seconds via @Scheduled
    // ─────────────────────────────────────────
    public Long likePost(Long postId) {
        String key = LIKE_KEY_PREFIX + postId;
        Long newCount = redisTemplate.opsForValue().increment(key);
        log.info("WRITE-BEHIND — like count for post {} is now {} in Redis", postId, newCount);
        return newCount;
    }

    // Runs every 30 seconds — flushes Redis like counts to MySQL
    @Scheduled(fixedRate = 30000)
    public void flushLikesToDb() {
        Set<String> keys = redisTemplate.keys(LIKE_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        log.info("FLUSH — writing {} post like counts to MySQL", keys.size());
        for (String key : keys) {
            Long postId = Long.parseLong(key.replace(LIKE_KEY_PREFIX, ""));
            Object val = redisTemplate.opsForValue().getAndDelete(key);
            if (val == null) continue;

            Long count = Long.parseLong(val.toString());
            postRepository.findById(postId).ifPresent(post -> {
                post.setLikeCount(post.getLikeCount() + count);
                postRepository.save(post);
                log.info("Flushed {} likes for post {}", count, postId);
            });
        }
    }

    // ─────────────────────────────────────────
    // DELETE POST — evict from cache
    // ─────────────────────────────────────────
    @CacheEvict(value = "posts", key = "#postId")
    public void deletePost(Long postId, String email) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!post.getAuthor().getEmail().equals(email)) {
            throw new RuntimeException("Not authorized to delete this post");
        }

        postRepository.delete(post);
        log.info("Post {} deleted and evicted from cache", postId);
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