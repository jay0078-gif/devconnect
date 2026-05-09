package com.devconnect.backend.service;

import com.devconnect.backend.dto.CreatePostRequest;
import com.devconnect.backend.dto.PostDto;
import com.devconnect.backend.entity.Post;
import com.devconnect.backend.entity.User;
import com.devconnect.backend.kafka.NotificationEvent;
import com.devconnect.backend.kafka.NotificationProducer;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationProducer notificationProducer; // ← NEW

    private static final String LIKE_KEY_PREFIX = "likes:post:";

    // ─────────────────────────────────────────
    // CREATE POST — Write-through + Kafka event
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
        redisTemplate.opsForValue().set("post:" + saved.getId(), dto);
        log.info("Post {} cached in Redis (write-through)", saved.getId());

        // Kafka: publish POST_CREATED event asynchronously
        // Controller returns INSTANTLY — Kafka handles delivery in background
        notificationProducer.sendPostEvent(new NotificationEvent(
                "POST_CREATED",
                author.getId(),
                author.getUsername(),
                author.getId(),
                saved.getId(),
                author.getUsername() + " created a new post: " + saved.getTitle()
        ));

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
    // LIKE — Write-behind + Kafka notification
    // ─────────────────────────────────────────
    public Long likePost(Long postId, String actorEmail) {
        String key = LIKE_KEY_PREFIX + postId;
        Long newCount = redisTemplate.opsForValue().increment(key);
        log.info("WRITE-BEHIND — like count for post {} is now {} in Redis", postId, newCount);

        // Kafka: notify post author that someone liked their post
        // This runs async — like response returns instantly
        postRepository.findById(postId).ifPresent(post -> {
            User actor = userRepository.findByEmail(actorEmail).orElse(null);
            if (actor == null) return;

            notificationProducer.sendNotification(new NotificationEvent(
                    "POST_LIKED",
                    actor.getId(),
                    actor.getUsername(),
                    post.getAuthor().getId(),  // recipient = post author
                    postId,
                    actor.getUsername() + " liked your post: " + post.getTitle()
            ));
        });

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