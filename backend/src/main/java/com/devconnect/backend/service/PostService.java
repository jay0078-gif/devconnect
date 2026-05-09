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
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationProducer notificationProducer;
    private final FollowService followService;

    private static final String LIKE_KEY_PREFIX = "likes:post:";

    // @Lazy on FollowService breaks the circular dependency
    // PostService → FollowService → PostRepository → (no PostService)
    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       RedisTemplate<String, Object> redisTemplate,
                       NotificationProducer notificationProducer,
                       @Lazy FollowService followService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.notificationProducer = notificationProducer;
        this.followService = followService;
    }

    // ─────────────────────────────────────────
    // CREATE POST
    // 1. Save to MySQL
    // 2. Write-through to Redis
    // 3. Fan-out to all followers' feeds
    // 4. Kafka event
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

        // Step 2: Write-through cache
        redisTemplate.opsForValue().set("post:" + saved.getId(), dto);
        log.info("Post {} cached in Redis (write-through)", saved.getId());

        // Step 3: Fan-out to all followers' Redis feeds
        long timestamp = saved.getCreatedAt()
                .toEpochSecond(ZoneOffset.UTC);
        followService.fanOutNewPost(author.getId(), saved.getId(), timestamp);

        // Step 4: Kafka event
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
    // GET FEED — paginated global feed (not personalized)
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
        log.info("WRITE-BEHIND — like count for post {} is now {} in Redis",
                postId, newCount);

        postRepository.findById(postId).ifPresent(post -> {
            User actor = userRepository.findByEmail(actorEmail).orElse(null);
            if (actor == null) return;

            notificationProducer.sendNotification(new NotificationEvent(
                    "POST_LIKED",
                    actor.getId(),
                    actor.getUsername(),
                    post.getAuthor().getId(),
                    postId,
                    actor.getUsername() + " liked your post: " + post.getTitle()
            ));
        });

        return newCount;
    }

    // Flush like counts to MySQL every 30 seconds
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