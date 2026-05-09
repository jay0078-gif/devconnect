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
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PostRepository postRepository;
    private final PostQueryService postQueryService;

    private static final String TRENDING_KEY = "trending:posts";
    private static final String TRENDING_DAILY_KEY = "trending:posts:daily";
    private static final int TOP_N = 10;

    public void incrementTrendingScore(Long postId) {
        redisTemplate.opsForZSet().incrementScore(TRENDING_KEY, postId.toString(), 1.0);
        redisTemplate.opsForZSet().incrementScore(TRENDING_DAILY_KEY, postId.toString(), 1.0);
        redisTemplate.expire(TRENDING_DAILY_KEY, Duration.ofHours(24));
        log.info("TRENDING — incremented score for post {}", postId);
    }

    public List<PostDto> getTopTrending() {
        Set<Object> postIds = redisTemplate.opsForZSet()
                .reverseRange(TRENDING_KEY, 0, TOP_N - 1);

        if (postIds == null || postIds.isEmpty()) {
            log.info("TRENDING — no data in Redis, falling back to DB");
            return getFallbackTrending();
        }

        log.info("TRENDING HIT — serving top {} from Redis", postIds.size());
        List<PostDto> posts = new ArrayList<>();
        for (Object id : postIds) {
            try {
                posts.add(postQueryService.getPostById(Long.parseLong(id.toString())));
            } catch (Exception e) {
                log.warn("Post {} not found, skipping", id);
            }
        }
        return posts;
    }

    public List<PostDto> getDailyTrending() {
        Set<Object> postIds = redisTemplate.opsForZSet()
                .reverseRange(TRENDING_DAILY_KEY, 0, TOP_N - 1);

        if (postIds == null || postIds.isEmpty()) {
            log.info("DAILY TRENDING — no data yet");
            return List.of();
        }

        List<PostDto> posts = new ArrayList<>();
        for (Object id : postIds) {
            try {
                posts.add(postQueryService.getPostById(Long.parseLong(id.toString())));
            } catch (Exception e) {
                log.warn("Post {} not found, skipping", id);
            }
        }
        return posts;
    }

    public List<Map<String, Object>> getTrendingWithScores() {
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate
                .opsForZSet()
                .reverseRangeWithScores(TRENDING_KEY, 0, TOP_N - 1);

        if (tuples == null || tuples.isEmpty()) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
            try {
                if (tuple.getValue() == null || tuple.getScore() == null) continue;
                Long postId = Long.parseLong(tuple.getValue().toString());
                PostDto post = postQueryService.getPostById(postId);
                result.add(Map.of(
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

    private List<PostDto> getFallbackTrending() {
        return postRepository
                .findAllByOrderByCreatedAtDesc(
                        org.springframework.data.domain.PageRequest.of(0, TOP_N))
                .map(post -> postQueryService.getPostById(post.getId()))
                .toList();
    }

    @Scheduled(cron = "0 0 0 * * MON")
    public void resetWeeklyTrending() {
        redisTemplate.delete(TRENDING_KEY);
        log.info("TRENDING — weekly reset complete");
    }
}