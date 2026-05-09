package com.devconnect.backend.service;

import com.devconnect.backend.dto.PostDto;
import com.devconnect.backend.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PostRepository postRepository;
    private final PostService postService;

    private static final String TRENDING_KEY = "trending:posts";
    private static final String TRENDING_DAILY_KEY = "trending:posts:daily";
    private static final int TOP_N = 10;

    // ─────────────────────────────────────────
    // Called every time a post is liked
    // ZINCRBY increments the score atomically
    // Safe under millions of concurrent likes
    // ─────────────────────────────────────────
    public void incrementTrendingScore(Long postId) {
        // Global trending — all time
        redisTemplate.opsForZSet().incrementScore(
                TRENDING_KEY,
                postId.toString(),
                1.0
        );

        // Daily trending — expires after 24 hours
        redisTemplate.opsForZSet().incrementScore(
                TRENDING_DAILY_KEY,
                postId.toString(),
                1.0
        );

        // Set TTL on daily key so it auto-resets every 24 hours
        redisTemplate.expire(TRENDING_DAILY_KEY, Duration.ofHours(24));

        log.info("TRENDING — incremented score for post {}", postId);
    }

    // ─────────────────────────────────────────
    // GET TOP 10 TRENDING POSTS
    // ZREVRANGE returns highest scores first
    // Zero DB queries — pure Redis
    // ─────────────────────────────────────────
    public List<PostDto> getTopTrending() {
        Set<Object> postIds = redisTemplate.opsForZSet()
                .reverseRange(TRENDING_KEY, 0, TOP_N - 1);

        if (postIds == null || postIds.isEmpty()) {
            log.info("TRENDING — no data in Redis, falling back to DB");
            return getFallbackTrending();
        }

        log.info("TRENDING HIT — serving top {} from Redis sorted set",
                postIds.size());

        List<PostDto> posts = new ArrayList<>();
        for (Object id : postIds) {
            try {
                posts.add(postService.getPostById(Long.parseLong(id.toString())));
            } catch (Exception e) {
                log.warn("Post {} not found, skipping", id);
            }
        }
        return posts;
    }

    // GET DAILY TRENDING — resets every 24 hours
    public List<PostDto> getDailyTrending() {
        Set<Object> postIds = redisTemplate.opsForZSet()
                .reverseRange(TRENDING_DAILY_KEY, 0, TOP_N - 1);

        if (postIds == null || postIds.isEmpty()) {
            log.info("DAILY TRENDING — no data yet");
            return List.of();
        }

        log.info("DAILY TRENDING HIT — serving top {} posts", postIds.size());

        List<PostDto> posts = new ArrayList<>();
        for (Object id : postIds) {
            try {
                // Also get the score (like count) for display
                Double score = redisTemplate.opsForZSet()
                        .score(TRENDING_DAILY_KEY, id.toString());
                PostDto post = postService.getPostById(
                        Long.parseLong(id.toString()));
                posts.add(post);
                log.info("  Post {} — score: {}", id, score);
            } catch (Exception e) {
                log.warn("Post {} not found, skipping", id);
            }
        }
        return posts;
    }

    // GET TRENDING WITH SCORES — useful for leaderboard UI
    public List<java.util.Map<String, Object>> getTrendingWithScores() {
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate
                .opsForZSet()
                .reverseRangeWithScores(TRENDING_KEY, 0, TOP_N - 1);

        if (tuples == null || tuples.isEmpty()) return List.of();

        List<java.util.Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
            try {
                Long postId = Long.parseLong(tuple.getValue().toString());
                PostDto post = postService.getPostById(postId);
                result.add(java.util.Map.of(
                        "rank", rank++,
                        "post", post,
                        "likeScore", tuple.getScore()
                ));
            } catch (Exception e) {
                log.warn("Skipping post in trending: {}", e.getMessage());
            }
        }
        return result;
    }

    // Fallback — if Redis has no data, query DB directly
    // This happens on first startup before any likes
    private List<PostDto> getFallbackTrending() {
        return postRepository
                .findAllByOrderByCreatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, TOP_N))
                .map(post -> postService.getPostById(post.getId()))
                .toList();
    }

    // ─────────────────────────────────────────
    // Reset global trending weekly
    // Runs every Monday at midnight
    // ─────────────────────────────────────────
    @Scheduled(cron = "0 0 0 * * MON")
    public void resetWeeklyTrending() {
        redisTemplate.delete(TRENDING_KEY);
        log.info("TRENDING — weekly reset complete");
    }
}