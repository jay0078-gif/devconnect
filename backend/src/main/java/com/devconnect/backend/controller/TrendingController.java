package com.devconnect.backend.controller;

import com.devconnect.backend.dto.PostDto;
import com.devconnect.backend.service.TrendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trending")
@RequiredArgsConstructor
public class TrendingController {

    private final TrendingService trendingService;

    // GET /api/trending — top 10 all time
    @GetMapping
    public ResponseEntity<List<PostDto>> getTopTrending() {
        return ResponseEntity.ok(trendingService.getTopTrending());
    }

    // GET /api/trending/daily — resets every 24 hours
    @GetMapping("/daily")
    public ResponseEntity<List<PostDto>> getDailyTrending() {
        return ResponseEntity.ok(trendingService.getDailyTrending());
    }

    // GET /api/trending/scores — with like scores for leaderboard
    @GetMapping("/scores")
    public ResponseEntity<List<Map<String, Object>>> getTrendingWithScores() {
        return ResponseEntity.ok(trendingService.getTrendingWithScores());
    }
}