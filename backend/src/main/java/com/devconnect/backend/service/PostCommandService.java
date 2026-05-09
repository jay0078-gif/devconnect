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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostCommandService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationProducer notificationProducer;
    private final FollowService followService;
    private final TrendingService trendingService;

    private static final String LIKE_KEY_PREFIX = "likes:post:";

    // ── COMMAND: Create Post ──
    // Write side only: save to MySQL, fan-out, Kafka event
    // Read side (cache) is updated separately via PostQueryService
    public PostDto createPost(String email, CreatePostRequest request) {
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = new Post();
        post.setTitle(request.getTitle());
        post.setContent(request.getContent());
        post.setAuthor(author);

        Post saved = postRepository.save(post);
        log.info("COMMAND — post created by {} with id {}", email, saved.getId());

        PostDto dto = mapToDto(saved);

        // Write-through: populate read cache immediately after write
        // This is the bridge between command and query side
        redisTemplate.opsForValue().set("post:" + saved.getId(), dto);
        log.info("COMMAND → QUERY sync: post {} written to Redis read cache",
                saved.getId());

        // Fan-out to followers' feeds
        long timestamp = saved.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
        followService.fanOutNewPost(author.getId(), saved.getId(), timestamp);

        // Kafka event
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

    // ── COMMAND: Like Post ──
    public Long likePost(Long postId, String actorEmail) {
        String key = LIKE_KEY_PREFIX + postId;
        Long newCount = redisTemplate.opsForValue().increment(key);
        log.info("COMMAND — like count for post {} is now {} in Redis",
                postId, newCount);

        trendingService.incrementTrendingScore(postId);

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

    // ── COMMAND: Delete Post ──
    public void deletePost(Long postId, String email) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (!post.getAuthor().getEmail().equals(email)) {
            throw new RuntimeException("Not authorized");
        }
        postRepository.delete(post);
        // Evict from read cache
        redisTemplate.delete("post:" + postId);
        log.info("COMMAND — post {} deleted, read cache evicted", postId);
    }

    // ── Flush likes to DB every 30s ──
    @Scheduled(fixedRate = 30000)
    public void flushLikesToDb() {
        Set<String> keys = redisTemplate.keys(LIKE_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;
        log.info("COMMAND FLUSH — writing {} like counts to MySQL", keys.size());
        for (String key : keys) {
            Long postId = Long.parseLong(key.replace(LIKE_KEY_PREFIX, ""));
            Object val = redisTemplate.opsForValue().getAndDelete(key);
            if (val == null) continue;
            Long count = Long.parseLong(val.toString());
            postRepository.findById(postId).ifPresent(post -> {
                post.setLikeCount(post.getLikeCount() + count);
                postRepository.save(post);
            });
        }
    }

    public PostDto mapToDto(Post post) {
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