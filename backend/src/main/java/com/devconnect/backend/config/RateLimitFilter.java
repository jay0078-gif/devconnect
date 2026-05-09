package com.devconnect.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    // Limits per minute per user
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final int MAX_LIKE_REQUESTS_PER_MINUTE = 30;
    private static final int MAX_POST_REQUESTS_PER_MINUTE = 10;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip rate limiting for auth endpoints
        if (path.startsWith("/api/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        // Get user identity from JWT (already validated by JwtFilter)
        // Use IP as fallback for unauthenticated requests
        String identity = getUserIdentity(request);

        // Choose limit based on endpoint sensitivity
        int limit = getLimit(path, method);

        // Build Redis key — sliding window per user per endpoint type
        String rateLimitKey = "rate:" + getEndpointType(path, method)
                + ":" + identity;

        // INCR — atomic increment, returns new count
        Long currentCount = redisTemplate.opsForValue().increment(rateLimitKey);

        // Set TTL on first request — window expires after 60 seconds
        if (currentCount == 1) {
            redisTemplate.expire(rateLimitKey, Duration.ofMinutes(1));
        }

        // Add rate limit headers so client knows their usage
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, limit - currentCount)));

        if (currentCount > limit) {
            log.warn("RATE LIMIT EXCEEDED — user: {}, path: {}, count: {}",
                    identity, path, currentCount);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please wait 60 seconds.\","
                            + "\"limit\":" + limit + ","
                            + "\"current\":" + currentCount + "}"
            );
            return;
        }

        log.debug("RATE LIMIT — user: {}, path: {}, count: {}/{}",
                identity, path, currentCount, limit);

        chain.doFilter(request, response);
    }

    // Extract user email from Spring Security context
    // This is already set by JwtFilter so no extra DB call needed
    private String getUserIdentity(HttpServletRequest request) {
        var auth = org.springframework.security.core.context
                .SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getName(); // email from JWT
        }

        // Fallback to IP address
        String ip = request.getHeader("X-Forwarded-For");
        return ip != null ? ip : request.getRemoteAddr();
    }

    // Different limits for different endpoint types
    private int getLimit(String path, String method) {
        if (path.contains("/like")) {
            return MAX_LIKE_REQUESTS_PER_MINUTE; // 30 likes/min
        }
        if (path.startsWith("/api/posts") && method.equals("POST")) {
            return MAX_POST_REQUESTS_PER_MINUTE; // 10 posts/min
        }
        return MAX_REQUESTS_PER_MINUTE; // 60 requests/min default
    }

    private String getEndpointType(String path, String method) {
        if (path.contains("/like")) return "like";
        if (path.startsWith("/api/posts") && method.equals("POST")) return "post";
        return "global";
    }
}