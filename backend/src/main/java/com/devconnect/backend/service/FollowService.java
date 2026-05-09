package com.devconnect.backend.service;

import com.devconnect.backend.entity.Follow;
import com.devconnect.backend.entity.Post;
import com.devconnect.backend.entity.User;
import com.devconnect.backend.kafka.NotificationEvent;
import com.devconnect.backend.kafka.NotificationProducer;
import com.devconnect.backend.repository.FollowRepository;
import com.devconnect.backend.repository.PostRepository;
import com.devconnect.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationProducer notificationProducer;

    private static final String FEED_KEY_PREFIX = "feed:user:";
    private static final long MAX_FEED_SIZE = 100; // keep last 100 posts per user

    // ─────────────────────────────────────────
    // FOLLOW — fan-out on write
    // When A follows B, we push B's last 10 posts into A's feed cache
    // So A's feed is instantly populated without any DB query
    // ─────────────────────────────────────────
    public String follow(String followerEmail, Long followingId) {
        User follower = userRepository.findByEmail(followerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new RuntimeException("User to follow not found"));

        if (follower.getId().equals(followingId)) {
            throw new RuntimeException("Cannot follow yourself");
        }

        if (followRepository.existsByFollowerIdAndFollowingId(
                follower.getId(), followingId)) {
            throw new RuntimeException("Already following this user");
        }

        // Save follow relationship to DB
        Follow follow = new Follow();
        follow.setFollower(follower);
        follow.setFollowing(following);
        followRepository.save(follow);
        log.info("{} followed {}", follower.getUsername(), following.getUsername());

        // ── FAN-OUT ON FOLLOW ──
        // Push the followed user's last 10 posts into follower's Redis feed
        // This is "backfill" — so new follower sees content immediately
        String feedKey = FEED_KEY_PREFIX + follower.getId();
        List<Post> recentPosts = postRepository
                .findByAuthorIdOrderByCreatedAtDesc(followingId,
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent();

        for (Post post : recentPosts) {
            // Redis sorted set: score = timestamp, value = postId
            // ZADD feed:user:1 timestamp postId
            redisTemplate.opsForZSet().add(
                    feedKey,
                    post.getId().toString(),
                    post.getCreatedAt().toEpochSecond(
                            java.time.ZoneOffset.UTC)
            );
        }

        // Trim feed to MAX_FEED_SIZE — remove oldest entries beyond 100
        redisTemplate.opsForZSet().removeRange(feedKey, 0,
                -(MAX_FEED_SIZE + 1));

        log.info("FAN-OUT — backfilled {} posts into {}'s feed",
                recentPosts.size(), follower.getUsername());

        // Kafka: notify the followed user
        notificationProducer.sendNotification(new NotificationEvent(
                "USER_FOLLOWED",
                follower.getId(),
                follower.getUsername(),
                followingId,
                null,
                follower.getUsername() + " started following you"
        ));

        return follower.getUsername() + " now follows " + following.getUsername();
    }

    // ─────────────────────────────────────────
    // UNFOLLOW — remove from DB, invalidate feed
    // ─────────────────────────────────────────
    public String unfollow(String followerEmail, Long followingId) {
        User follower = userRepository.findByEmail(followerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Follow follow = followRepository
                .findByFollowerIdAndFollowingId(follower.getId(), followingId)
                .orElseThrow(() -> new RuntimeException("Not following this user"));

        followRepository.delete(follow);

        // Remove unfollowed user's posts from follower's feed
        // We delete the entire feed key — it will rebuild on next fan-out
        redisTemplate.delete(FEED_KEY_PREFIX + follower.getId());
        log.info("UNFOLLOW — {} unfollowed user {}, feed cache cleared",
                follower.getUsername(), followingId);

        return "Unfollowed successfully";
    }

    // ─────────────────────────────────────────
    // GET FEED — read from Redis sorted set
    // Returns post IDs sorted by timestamp (newest first)
    // ─────────────────────────────────────────
    public List<Long> getFeedPostIds(String email, int page, int size) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String feedKey = FEED_KEY_PREFIX + user.getId();
        long start = (long) page * size;
        long end = start + size - 1;

        // ZREVRANGE — get post IDs newest first
        var postIdStrings = redisTemplate.opsForZSet()
                .reverseRange(feedKey, start, end);

        if (postIdStrings == null || postIdStrings.isEmpty()) {
            log.info("FEED MISS — no feed in Redis for {}, returning empty",
                    user.getUsername());
            return List.of();
        }

        log.info("FEED HIT — serving {} posts from Redis for {}",
                postIdStrings.size(), user.getUsername());

        return postIdStrings.stream()
                .map(id -> Long.parseLong(id.toString()))
                .toList();
    }

    // Fan-out when a new post is created
    // Called from PostService after saving — pushes postId to all followers' feeds
    public void fanOutNewPost(Long authorId, Long postId, long timestamp) {
        List<Long> followerIds = followRepository
                .findFollowerIdsByFollowingId(authorId);

        log.info("FAN-OUT — pushing post {} to {} followers' feeds",
                postId, followerIds.size());

        for (Long followerId : followerIds) {
            String feedKey = FEED_KEY_PREFIX + followerId;
            redisTemplate.opsForZSet().add(
                    feedKey,
                    postId.toString(),
                    timestamp
            );
            // Trim each follower's feed to 100 posts
            redisTemplate.opsForZSet().removeRange(
                    feedKey, 0, -(MAX_FEED_SIZE + 1));
        }
    }

    public long getFollowerCount(Long userId) {
        return followRepository.countByFollowingId(userId);
    }

    public long getFollowingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }
}